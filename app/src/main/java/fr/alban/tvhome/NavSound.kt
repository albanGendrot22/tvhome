package fr.alban.tvhome

import android.content.Context
import android.media.AudioManager

/**
 * Son de navigation : les sons système du focus (haut / bas / gauche / droite) via AudioManager, volume 0,6.
 * Aucun SoundPool, aucune ressource embarquée. Respecte le réglage système « sons de navigation ».
 */
object NavSound {
    private var lastRow = -1
    private var lastCol = -1
    private var audio: AudioManager? = null

    /** Appelé quand une tuile prend le focus : la direction se déduit de la case précédente. */
    fun onFocus(ctx: Context, row: Int, col: Int) {
        val dRow = row - lastRow
        val dCol = col - lastCol
        val first = lastRow < 0
        lastRow = row
        lastCol = col
        if (first || !UiSettings.navSound.value) return
        val fx = when {
            dRow < 0 -> AudioManager.FX_FOCUS_NAVIGATION_UP
            dRow > 0 -> AudioManager.FX_FOCUS_NAVIGATION_DOWN
            dCol < 0 -> AudioManager.FX_FOCUS_NAVIGATION_LEFT
            dCol > 0 -> AudioManager.FX_FOCUS_NAVIGATION_RIGHT
            else -> return
        }
        val am = audio ?: (ctx.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager).also { audio = it }
        am.playSoundEffect(fx, VOLUME)
    }

    private const val VOLUME = 0.6f
}
