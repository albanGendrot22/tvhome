package fr.alban.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.PlaybackState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Adapté de tvOS Launcher (techgenius1), Apache 2.0 : « Continue Watching » à partir des sessions média
// que les applis publient pendant la lecture (MediaWatchService).

/** Une lecture à reprendre : ce que l'appli a publié au système, gardé pour la rangée « Reprendre la lecture ». */
class ResumeItem(
    val pkg: String,
    val title: String,
    val subtitle: String,
    val position: Long,
    val duration: Long,
    val art: ImageBitmap?,
) {
    /** 0..1, ou 0 si l'appli ne donne pas de durée (direct). */
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/**
 * Mémorise les dernières lectures, une par appli (les 6 plus récentes), dans les préférences et un petit dossier
 * de vignettes. Alimenté par [MediaListener] ; lu à l'ouverture de l'accueil, jamais interrogé en boucle.
 */
object ContinueStore {
    private const val PREFS = "resume"
    private const val KEY = "items"
    private const val MAX = 6
    private const val EXPIRE_MS = 30L * 24 * 3600 * 1000

    /** Change à chaque nouvelle lecture notée : l'accueil recharge la rangée. */
    val changes = MutableStateFlow(0)

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun dir(ctx: Context) = File(ctx.filesDir, "resume").apply { mkdirs() }

    /** À appeler à chaque changement de métadonnées ou d'état de lecture d'une session. */
    fun record(ctx: Context, pkg: String, metadata: MediaMetadata?, state: PlaybackState?) {
        if (pkg == ctx.packageName) return
        val md = metadata ?: return
        val title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
            ?: md.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: return
        val subtitle = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.takeIf { it.isNotBlank() }
            ?: md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0)
        val position = state?.position?.coerceAtLeast(0) ?: 0
        val s = state?.state
        // Lecture terminée (95 % ou plus) : plus rien à reprendre
        if (duration > 0 && position >= duration * 0.95f && s != PlaybackState.STATE_PLAYING) { remove(ctx, pkg); return }
        if (s != PlaybackState.STATE_PLAYING && s != PlaybackState.STATE_PAUSED && s != PlaybackState.STATE_STOPPED) return

        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art != null) saveArt(ctx, pkg, art)

        val items = readJson(ctx).filter { it.getString("pkg") != pkg }.toMutableList()
        items.add(0, JSONObject().put("pkg", pkg).put("title", title).put("sub", subtitle)
            .put("pos", position).put("dur", duration).put("at", System.currentTimeMillis()))
        write(ctx, items.take(MAX))
    }

    fun remove(ctx: Context, pkg: String) {
        write(ctx, readJson(ctx).filter { it.getString("pkg") != pkg })
        File(dir(ctx), "$pkg.jpg").delete()
    }

    fun load(ctx: Context): List<ResumeItem> {
        val now = System.currentTimeMillis()
        return readJson(ctx).filter { now - it.optLong("at") < EXPIRE_MS }.map {
            val pkg = it.getString("pkg")
            ResumeItem(
                pkg, it.getString("title"), it.optString("sub"), it.optLong("pos"), it.optLong("dur"),
                File(dir(ctx), "$pkg.jpg").takeIf { f -> f.exists() }
                    ?.let { f -> BitmapFactory.decodeFile(f.path)?.asImageBitmap() },
            )
        }
    }

    private fun saveArt(ctx: Context, pkg: String, bmp: Bitmap) {
        runCatching {
            // Vignette de 320 px de large : assez pour une carte, léger à décoder
            val w = 320
            val h = (bmp.height * w / bmp.width.coerceAtLeast(1)).coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(bmp, w, h, true)
            File(dir(ctx), "$pkg.jpg").outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        }
    }

    private fun readJson(ctx: Context): List<JSONObject> = runCatching {
        val a = JSONArray(sp(ctx).getString(KEY, "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun write(ctx: Context, items: List<JSONObject>) {
        sp(ctx).edit().putString(KEY, JSONArray(items).toString()).apply()
        changes.value++
    }

    /** Pour les essais (adb : --ez resume_demo true) : une lecture fictive de Netflix. */
    fun demo(ctx: Context) {
        val items = readJson(ctx).filter { it.getString("pkg") != "com.netflix.ninja" }.toMutableList()
        items.add(0, JSONObject().put("pkg", "com.netflix.ninja").put("title", "Un job mortel")
            .put("sub", "Saison 1 · Épisode 3").put("pos", 1_320_000L).put("dur", 2_900_000L).put("at", System.currentTimeMillis()))
        write(ctx, items.take(MAX))
    }
}
