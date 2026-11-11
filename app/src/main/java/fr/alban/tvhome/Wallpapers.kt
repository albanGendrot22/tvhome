package fr.alban.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Un fond net pour la Top Shelf, et sa version floutée pour quand on descend dans la grille. */
class Wallpaper(val sharp: ImageBitmap, val blurred: ImageBitmap)

object Wallpapers {
    private const val MAX_W = 1280  // l'interface de la TV tourne en 1280x720
    private const val BLUR_W = 160

    /** Dossier où pousser tes photos : /sdcard/Android/data/fr.alban.tvhome/files/wallpapers */
    fun files(ctx: Context): List<File> =
        ctx.getExternalFilesDir("wallpapers")?.listFiles().orEmpty()
            .filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            .sortedBy { it.name }

    fun load(file: File): Wallpaper? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_W) sample *= 2

        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565 // pas de transparence dans une photo : 2x moins lourd
        })
            ?: return null
        val sharp = if (decoded.width > MAX_W) {
            Bitmap.createScaledBitmap(decoded, MAX_W, decoded.height * MAX_W / decoded.width, true)
                .also { decoded.recycle() }
        } else decoded

        // Pas de flou temps réel sur Android 11 : on floute une mini-version une seule fois.
        val bh = (sharp.height * BLUR_W / sharp.width).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(sharp, BLUR_W, bh, true)
        val px = IntArray(BLUR_W * bh)
        small.getPixels(px, 0, BLUR_W, 0, 0, BLUR_W, bh)
        var out = px
        repeat(3) { out = boxBlur(out, BLUR_W, bh, 5) }
        val blurred = Bitmap.createBitmap(out, BLUR_W, bh, Bitmap.Config.ARGB_8888)

        Wallpaper(sharp.asImageBitmap(), blurred.asImageBitmap())
    }.getOrNull()

    internal fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(src.size)
        val out = IntArray(src.size)
        pass(src, tmp, w, h, r, horizontal = true)
        pass(tmp, out, w, h, r, horizontal = false)
        return out
    }

    private fun pass(inp: IntArray, out: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val d = 2 * r + 1
        for (l in 0 until lines) {
            fun idx(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) l * w + c else c * w + l
            }
            var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val p = inp[idx(i)]
                sr += p shr 16 and 0xFF; sg += p shr 8 and 0xFF; sb += p and 0xFF
            }
            for (i in 0 until len) {
                out[idx(i)] = (0xFF shl 24) or ((sr / d) shl 16) or ((sg / d) shl 8) or (sb / d)
                val pOut = inp[idx(i - r)]
                val pIn = inp[idx(i + r + 1)]
                sr += (pIn shr 16 and 0xFF) - (pOut shr 16 and 0xFF)
                sg += (pIn shr 8 and 0xFF) - (pOut shr 8 and 0xFF)
                sb += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }
    }
}

/**
 * Faux verre dépoli : la photo floutée précalculée, cadrée exactement comme le fond plein écran (Crop),
 * mais peinte seulement sur la zone de l'élément, puis une teinte unie par-dessus. Opaque : ni flou temps réel,
 * ni calque hors écran. [origin] = coin haut-gauche de l'élément à l'écran, [screen] = taille de l'écran.
 */
fun DrawScope.drawFrosted(img: ImageBitmap, origin: Offset, screen: Size, tint: Color) {
    val s = maxOf(screen.width / img.width, screen.height / img.height)
    val w = img.width * s
    val h = img.height * s
    clipRect {
        drawImage(
            img,
            dstOffset = IntOffset(((screen.width - w) / 2 - origin.x).roundToInt(), ((screen.height - h) / 2 - origin.y).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
    drawRect(tint)
}

/**
 * Deux photos seulement : `nuit.jpg` quand il fait nuit dehors (lever et coucher du soleil de la météo),
 * `jour.jpg` le reste du temps. À défaut, la première photo du dossier.
 */
/**
 * Capsule de verre dépoli de la première rangée, fabriquée une seule fois : la photo floutée à cet endroit
 * (un peu plus saturée), un voile clair, un reflet en haut, un liseré et des coins arrondis. À l'affichage c'est
 * une simple image dessinée telle quelle (aucun flou ni calque à chaque image).
 * [x0], [y0], [w], [h] : position et taille de la capsule à l'écran, en pixels.
 */
fun bakeCapsule(
    wp: Wallpaper, screenW: Int, screenH: Int, x0: Int, y0: Int, w: Int, h: Int, radius: Float, stroke: Float,
): ImageBitmap {
    val src = wp.sharp.asAndroidBitmap()
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    // La photo nette cadrée comme le fond plein écran (Crop), prise à la position de la capsule, réduite au quart
    // puis légèrement floutée : du verre dépoli, on devine encore le décor (la version très floue serait unie)
    val q = 4
    val s = maxOf(screenW.toFloat() / src.width, screenH.toFloat() / src.height)
    val dw = src.width * s
    val dh = src.height * s
    val l = (screenW - dw) / 2f - x0
    val t = (screenH - dh) / 2f - y0
    val sw = (w / q).coerceAtLeast(1)
    val sh = (h / q).coerceAtLeast(1)
    val small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
    Canvas(small).drawBitmap(src, null, RectF(l / q, t / q, (l + dw) / q, (t + dh) / q), Paint(Paint.FILTER_BITMAP_FLAG))
    val px = IntArray(sw * sh)
    small.getPixels(px, 0, sw, 0, 0, sw, sh)
    var soft = px
    repeat(2) { soft = Wallpapers.boxBlur(soft, sw, sh, 4) }
    small.setPixels(soft, 0, sw, 0, 0, sw, sh)
    val photo = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.1f) })
    }
    c.drawBitmap(small, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), photo)
    small.recycle()
    // Même assombrissement que le fond à cet endroit (le dégradé du bas commence à 40 % de la hauteur et monte à 70 %)
    fun fondDark(y: Float) = (0.7f * ((y / screenH - 0.4f) / 0.6f)).coerceIn(0f, 0.7f)
    val a0 = (fondDark(y0.toFloat()) * 255).toInt()
    val a1 = (fondDark((y0 + h).toFloat()) * 255).toInt()
    c.drawRect(
        0f, 0f, w.toFloat(), h.toFloat(),
        Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), a0 shl 24, a1 shl 24, Shader.TileMode.CLAMP) },
    )
    // Le verre : un voile clair léger et un reflet en haut
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { color = 0x1AFFFFFF })
    c.drawRect(
        0f, 0f, w.toFloat(), h * 0.5f,
        Paint().apply { shader = LinearGradient(0f, 0f, 0f, h * 0.5f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP) },
    )
    c.drawRoundRect(
        RectF(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2), radius, radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = 0x59FFFFFF },
    )
    // Coins arrondis : on recopie l'image à travers un rectangle arrondi lissé (un masque DST_IN ne efface pas hors forme)
    val rounded = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(rounded).drawRoundRect(
        RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(out, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) },
    )
    out.recycle()
    return rounded.asImageBitmap()
}

@Composable
fun rememberWallpaper(ctx: Context, enabled: Boolean = true, night: Boolean = false): State<Wallpaper?> =
    produceState<Wallpaper?>(null, enabled, night) {
        // Fond uni : aucune photo décodée ni gardée en mémoire
        if (!enabled) { value = null; return@produceState }
        while (true) {
            val files = withContext(Dispatchers.IO) { Wallpapers.files(ctx) }
            val wanted = if (night) "nuit" else "jour"
            val file = files.firstOrNull { it.nameWithoutExtension == wanted } ?: files.firstOrNull()
            if (file != null) {
                withContext(Dispatchers.Default) { Wallpapers.load(file) }?.let { value = it }
                return@produceState
            }
            delay(30_000)
        }
    }
