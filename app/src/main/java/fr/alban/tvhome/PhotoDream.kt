package fr.alban.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Adapté de tvOS Launcher (techgenius1), Apache 2.0 : mode veille « StandBy » (horloge géante sur un paysage).

/**
 * Économiseur d'écran façon StandBy : la photo du moment (nuit.jpg la nuit, jour.jpg le jour) FIXE, une horloge
 * géante, rien qui bouge sauf l'horloge à la minute. La nuit, la photo est assombrie et l'horloge passe en teinte
 * chaude. L'horloge se décale un peu à chaque minute pour ne pas marquer l'écran.
 */
class PhotoDream : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var photo: ImageView
    private lateinit var dim: View
    private lateinit var clockBox: LinearLayout
    private lateinit var time: TextView
    private lateinit var date: TextView
    private var night = false

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        photo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(photo, FrameLayout.LayoutParams(-1, -1))
        dim = View(this)
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))
        // Pied de page sombre : l'horloge reste lisible sur une photo claire
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(Color.argb(150, 0, 0, 0), Color.TRANSPARENT))
        }, FrameLayout.LayoutParams(-1, resources.displayMetrics.heightPixels / 2, Gravity.BOTTOM))

        time = TextView(this).apply {
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 150f)
            includeFontPadding = false
            setShadowLayer(18f, 0f, 3f, Color.argb(110, 0, 0, 0))
        }
        date = TextView(this).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setShadowLayer(12f, 0f, 2f, Color.argb(110, 0, 0, 0))
        }
        clockBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(time)
            addView(date)
        }
        root.addView(clockBox, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        // MainActivity note si c'est la nuit (lever et coucher du soleil de la météo) ; à défaut, 20 h – 7 h
        val h = LocalDateTime.now().hour
        night = getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("is_night", h < 7 || h >= 20)
        val tint = if (night) Color.rgb(255, 201, 163) else Color.WHITE
        time.setTextColor(tint)
        date.setTextColor(Color.argb(230, Color.red(tint), Color.green(tint), Color.blue(tint)))
        dim.setBackgroundColor(Color.argb(if (night) 120 else 40, 0, 0, 0))

        val files = getExternalFilesDir("wallpapers")?.listFiles().orEmpty()
            .filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
        val wanted = if (night) "nuit" else "jour"
        val file = files.firstOrNull { it.nameWithoutExtension == wanted } ?: files.firstOrNull()
        if (file != null) Thread { decode(file)?.let { bmp -> handler.post { photo.setImageBitmap(bmp) } } }.start()
        handler.post(tick)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacksAndMessages(null)
        (photo.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let {
            photo.setImageDrawable(null)
            if (!it.isRecycled) it.recycle()
        }
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private fun updateClock() {
        val now = LocalDateTime.now()
        time.text = now.format(timeFormat)
        date.text = now.format(dateFormat).replaceFirstChar { it.titlecase(Locale.FRANCE) }
        // Décalage d'au plus 30 dp, qui change à chaque minute : contre le marquage de l'écran
        val dp = resources.displayMetrics.density
        clockBox.translationX = (72 + (now.minute % 6) * 6) * dp
        clockBox.translationY = -(48 + (now.minute % 6) * 6) * dp
    }

    private fun decode(file: File): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1280) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    }.getOrNull()
}
