package fr.alban.tvhome

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TvApp(
    val pkg: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val intent: Intent,
    /** Couleur signature de l'appli (ARGB), tirée de sa bannière ou de son icône : liseré de la tuile sélectionnée. */
    val accent: Int = 0xFFFFFFFF.toInt(),
)

private const val SETTINGS_PKG = "com.android.tv.settings"

object AppRepository {
    // Les bannières coûtent cher à décoder : on les garde tant que l'app n'a pas été mise à jour.
    private val cache = HashMap<String, Pair<Long, TvApp>>()

    /** Applis visibles, dans l'ordre choisi, avec les noms personnalisés. */
    suspend fun load(ctx: Context): List<TvApp> {
        val hidden = Prefs.hidden(ctx)
        return Prefs.sort(ctx, all(ctx).filter { it.pkg !in hidden })
    }

    /** Applis masquées (le « coffre »), par ordre alphabétique. */
    suspend fun loadHidden(ctx: Context): List<TvApp> {
        val hidden = Prefs.hidden(ctx)
        return all(ctx).filter { it.pkg in hidden }.sortedBy { it.label.lowercase() }
    }

    private suspend fun all(ctx: Context): List<TvApp> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val infos = pm.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }

        val apps = infos.mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            val updated = runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)
            cache[pkg]?.takeIf { it.first == updated }?.second
                ?: build(pm, ri)?.also { cache[pkg] = updated to it }
        }.toMutableList()

        // Tuile Réglages, comme sur l'Apple TV, si le système ne la propose pas déjà
        if (apps.none { it.pkg == SETTINGS_PKG }) {
            val icon = runCatching { pm.getApplicationIcon(SETTINGS_PKG).toImage(96, 96) }.getOrNull()
            apps += TvApp(
                pkg = SETTINGS_PKG,
                label = "Réglages",
                banner = null,
                icon = icon,
                intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        val names = Prefs.renames(ctx)
        apps.map { a -> names[a.pkg]?.let { TvApp(a.pkg, it, a.banner, a.icon, a.intent, a.accent) } ?: a }
    }

    private fun build(pm: PackageManager, ri: ResolveInfo): TvApp? = runCatching {
        val ai = ri.activityInfo
        val banner = (ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm))?.toImage(320, 180)
        val icon = if (banner == null) ai.loadIcon(pm)?.toImage(96, 96) else null
        TvApp(
            pkg = ai.packageName,
            label = ri.loadLabel(pm).toString(),
            banner = banner,
            icon = icon,
            intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setClassName(ai.packageName, ai.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            accent = signatureColor((banner ?: icon)?.asAndroidBitmap()),
        )
    }.getOrNull()
}

/**
 * Couleur signature d'une image : la teinte dominante parmi les pixels colorés (ni blancs, ni noirs, ni gris),
 * éclaircie pour rester visible sur fond sombre. Calculée une fois au chargement de l'appli, jamais à l'affichage.
 * Blanc si l'image n'a pas de couleur (logo noir et blanc).
 */
private fun signatureColor(bmp: Bitmap?): Int {
    if (bmp == null) return 0xFFFFFFFF.toInt()
    val small = Bitmap.createScaledBitmap(bmp, 16, 9, true)
    val hsv = FloatArray(3)
    // Teintes regroupées en 12 secteurs, pondérées par la saturation et la valeur
    val weight = FloatArray(12)
    val hueSum = FloatArray(12)
    for (y in 0 until small.height) for (x in 0 until small.width) {
        val p = small.getPixel(x, y)
        if (android.graphics.Color.alpha(p) < 200) continue
        android.graphics.Color.colorToHSV(p, hsv)
        if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue
        val w = hsv[1] * hsv[2]
        val sector = (hsv[0] / 30f).toInt().coerceIn(0, 11)
        weight[sector] += w
        hueSum[sector] += hsv[0] * w
    }
    val best = weight.indices.maxByOrNull { weight[it] } ?: return 0xFFFFFFFF.toInt()
    if (weight[best] < 0.6f) return 0xFFFFFFFF.toInt()
    return android.graphics.Color.HSVToColor(floatArrayOf(hueSum[best] / weight[best], 0.75f, 1f))
}

private fun Drawable.toImage(w: Int, h: Int): ImageBitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, w, h)
    draw(Canvas(bmp))
    return bmp.asImageBitmap()
}

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("home", Context.MODE_PRIVATE)

    fun saveOrder(ctx: Context, apps: List<TvApp>) {
        sp(ctx).edit().putString("order", apps.joinToString(",") { it.pkg }).apply()
    }

    fun hidden(ctx: Context): Set<String> = sp(ctx).getStringSet("hidden", emptySet()).orEmpty()

    fun hide(ctx: Context, pkg: String) {
        sp(ctx).edit().putStringSet("hidden", hidden(ctx) + pkg).apply()
    }

    fun unhideAll(ctx: Context) {
        sp(ctx).edit().remove("hidden").apply()
    }

    fun unhide(ctx: Context, key: String) {
        sp(ctx).edit().putStringSet("hidden", hidden(ctx) - key).apply()
    }

    // Adapté de tvOS Launcher (techgenius1), Apache 2.0 : renommer une appli, dossier « Autres applis »

    /** Noms personnalisés, stockés « paquet|nom » (le nom peut contenir « | »). */
    fun renames(ctx: Context): Map<String, String> =
        sp(ctx).getStringSet("names", emptySet()).orEmpty().mapNotNull { e ->
            val i = e.indexOf('|')
            if (i > 0) e.substring(0, i) to e.substring(i + 1) else null
        }.toMap()

    /** [label] vide ou nul : retour au nom d'origine. */
    fun rename(ctx: Context, pkg: String, label: String?) {
        val kept = sp(ctx).getStringSet("names", emptySet()).orEmpty().filterNot { it.startsWith("$pkg|") }.toSet()
        val clean = label?.trim().orEmpty()
        sp(ctx).edit().putStringSet("names", if (clean.isEmpty()) kept else kept + "$pkg|$clean").apply()
    }

    /** Applis rangées dans le dossier « Autres applis », hors de la grille principale. */
    fun other(ctx: Context): Set<String> = sp(ctx).getStringSet("other", emptySet()).orEmpty()

    fun setOther(ctx: Context, pkg: String, on: Boolean) {
        sp(ctx).edit().putStringSet("other", if (on) other(ctx) + pkg else other(ctx) - pkg).apply()
    }

    // Adapté de tvOS Launcher (techgenius1), Apache 2.0 : applis récentes (RecentAppsOverlay)

    /** Les dernières applis lancées depuis l'accueil, la plus récente en premier. */
    fun recent(ctx: Context): List<String> =
        sp(ctx).getString("recent", "").orEmpty().split(",").filter { it.isNotBlank() }

    fun pushRecent(ctx: Context, pkg: String) {
        val list = (listOf(pkg) + recent(ctx).filter { it != pkg }).take(8)
        sp(ctx).edit().putString("recent", list.joinToString(",")).apply()
    }

    /** Ordre, dossier et noms reviennent à zéro ; les applis masquées le restent (coffre). */
    fun resetLayout(ctx: Context) {
        sp(ctx).edit().remove("order").remove("other").remove("names").apply()
    }

    fun sort(ctx: Context, apps: List<TvApp>): List<TvApp> {
        val order = sp(ctx).getString("order", null)?.split(",") ?: Config.DEFAULT_ORDER
        val rank = order.withIndex().associate { it.value to it.index }
        return apps.sortedWith(
            compareBy<TvApp> { rank[it.pkg] ?: Int.MAX_VALUE }
                .thenBy { if (it.pkg == SETTINGS_PKG) 1 else 0 }
                .thenBy { it.label.lowercase() }
        )
    }
}
