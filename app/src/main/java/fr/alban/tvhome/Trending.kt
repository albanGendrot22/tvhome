package fr.alban.tvhome

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Un titre tendance, disponible sur une appli installée. */
data class Pick(
    val id: Int,
    val type: String,
    val title: String,
    val overview: String,
    val provider: String,
    val pkg: String,
    val backdrop: String,
)

/**
 * Le Top Shelf façon Apple TV : les films et séries tendance de la semaine (TMDB),
 * rangés par appli, en ne gardant que ce qui est dispo en France dans les applis installées.
 */
object TrendingRepository {
    private const val TAG = "TvHomeTmdb"
    private const val API = "https://api.themoviedb.org/3"
    private const val IMAGES = "https://image.tmdb.org/t/p/w1280"
    private const val CACHE_MS = 6 * 3600_000L
    private const val MAX_PER_APP = 5

    /** Nom du service chez TMDB (en minuscules) → appli Android TV. */
    private val PROVIDERS = listOf(
        "netflix" to "com.netflix.ninja",
        "disney" to "com.disney.disneyplus",
        "amazon prime" to "com.amazon.amazonvideo.livingroom",
        "prime video" to "com.amazon.amazonvideo.livingroom",
        "apple tv" to "com.apple.atve.androidtv.appletv",
        "france.tv" to "fr.francetv.pluzz",
        "france tv" to "fr.francetv.pluzz",
        "arte" to "tv.arte.plus7",
        "m6" to "fr.m6.m6replay",
        "6play" to "fr.m6.m6replay",
        "anime digital" to "fr.anidn",
        "adn" to "fr.anidn",
    )

    private fun dir(ctx: Context) = File(ctx.cacheDir, "tmdb").apply { mkdirs() }

    /** Titres par appli. Servis depuis le cache disque s'il a moins de 6 h. */
    suspend fun load(ctx: Context, installed: Set<String>): Map<String, List<Pick>> = withContext(Dispatchers.IO) {
        if (!Config.TOP_SHELF_ENABLED || Config.TMDB_TOKEN.isBlank()) return@withContext emptyMap()
        val cache = File(dir(ctx), "picks.json")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < CACHE_MS
        val picks = (if (fresh) runCatching { decode(cache.readText()) }.getOrNull() else null)
            ?: runCatching { fetch(installed) }
                .onSuccess { if (it.isNotEmpty()) cache.writeText(encode(it)) }
                .onFailure { Log.w(TAG, "TMDB indisponible", it) }
                .getOrNull()
            ?: runCatching { decode(cache.readText()) }.getOrDefault(emptyList()) // vieux cache, faute de mieux
        picks.filter { it.pkg in installed }.groupBy { it.pkg }.mapValues { it.value.take(MAX_PER_APP) }
    }

    private fun fetch(installed: Set<String>): List<Pick> {
        val out = mutableListOf<Pick>()
        val seen = HashSet<String>()
        for (page in 1..3) {
            val results = get("/trending/all/week?language=fr-FR&page=$page").getJSONArray("results")
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                val type = r.optString("media_type")
                if (type != "movie" && type != "tv") continue
                val backdrop = r.optString("backdrop_path").takeIf { it.startsWith("/") } ?: continue
                val id = r.getInt("id")
                if (!seen.add("$type/$id")) continue
                val title = r.optString(if (type == "movie") "title" else "name")
                if (title.isBlank()) continue
                val provider = providerFor(type, id, installed) ?: continue
                out += Pick(
                    id = id,
                    type = type,
                    title = title,
                    overview = r.optString("overview"),
                    provider = provider.first,
                    pkg = provider.second,
                    backdrop = backdrop,
                )
            }
        }
        return out
    }

    /** Le premier service d'abonnement (pas la location) qui propose ce titre en France et qu'on a installé. */
    private fun providerFor(type: String, id: Int, installed: Set<String>): Pair<String, String>? {
        val fr = runCatching {
            get("/$type/$id/watch/providers").getJSONObject("results").optJSONObject("FR")
        }.getOrNull() ?: return null
        val offers = (fr.optJSONArray("flatrate") ?: JSONArray()).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } +
            (fr.optJSONArray("free") ?: JSONArray()).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } +
            (fr.optJSONArray("ads") ?: JSONArray()).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        for (o in offers) {
            val name = o.optString("provider_name")
            val lower = name.lowercase()
            val pkg = PROVIDERS.firstOrNull { lower.contains(it.first) }?.second ?: continue
            if (pkg in installed) return displayName(name) to pkg
        }
        return null
    }

    private fun displayName(tmdbName: String) = when {
        tmdbName.startsWith("Amazon Prime", ignoreCase = true) -> "Prime Video"
        tmdbName.startsWith("Disney", ignoreCase = true) -> "Disney+"
        tmdbName.startsWith("Apple TV", ignoreCase = true) -> "Apple TV"
        else -> tmdbName
    }

    private fun get(path: String): JSONObject {
        val conn = (URL(API + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Authorization", "Bearer ${Config.TMDB_TOKEN}")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != 200) throw java.io.IOException("TMDB HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /** Télécharge à l'avance les images manquantes, une par une, sans rien décoder. */
    suspend fun prefetch(ctx: Context, picks: Map<String, List<Pick>>) = withContext(Dispatchers.IO) {
        picks.values.flatten().forEach { download(ctx, it) }
    }

    private fun download(ctx: Context, pick: Pick): File {
        val file = File(dir(ctx), "${pick.type}-${pick.id}.jpg")
        if (!file.exists()) {
            runCatching {
                val tmp = File(file.path + ".part")
                val conn = URL(IMAGES + pick.backdrop).openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 15_000
                try {
                    conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                } finally {
                    conn.disconnect()
                }
                tmp.renameTo(file)
            }.onFailure { Log.w(TAG, "Image TMDB indisponible", it) }
            pruneImages(ctx)
        }
        return file
    }

    /** Lit l'image (téléchargée si besoin), puis la prépare comme un fond d'écran (nette + floutée). */
    suspend fun image(ctx: Context, pick: Pick): Wallpaper? = withContext(Dispatchers.IO) {
        val file = download(ctx, pick)
        if (!file.exists()) return@withContext null
        withContext(Dispatchers.Default) { Wallpapers.load(file) }
    }

    /** Garde au plus 60 images en cache (≈ 15 Mo). */
    private fun pruneImages(ctx: Context) {
        val imgs = dir(ctx).listFiles { f -> f.extension == "jpg" }.orEmpty().sortedByDescending { it.lastModified() }
        imgs.drop(60).forEach { it.delete() }
    }

    private fun encode(picks: List<Pick>): String = JSONArray().apply {
        picks.forEach {
            put(JSONObject().put("id", it.id).put("type", it.type).put("title", it.title)
                .put("overview", it.overview).put("provider", it.provider).put("pkg", it.pkg)
                .put("backdrop", it.backdrop))
        }
    }.toString()

    private fun decode(text: String): List<Pick> {
        val a = JSONArray(text)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Pick(o.getInt("id"), o.getString("type"), o.getString("title"), o.optString("overview"),
                o.getString("provider"), o.getString("pkg"), o.getString("backdrop"))
        }
    }
}
