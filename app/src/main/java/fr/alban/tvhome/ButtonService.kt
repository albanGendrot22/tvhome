package fr.alban.tvhome

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

// Adapté de tvOS Launcher (techgenius1), Apache 2.0 : raccourci télécommande par service d'accessibilité
// (ButtonMapper / PowerService). Ici un seul raccourci : appui long sur Retour = centre de contrôle.

/**
 * Appui long sur Retour, dans n'importe quelle appli : ouvre le centre de contrôle de l'accueil.
 * Un appui court n'est jamais touché. Le service ne lit aucun écran ni aucun texte : il ne reçoit que les touches
 * (et la TV les lui envoie une fois, l'appli reçoit la même touche en même temps).
 * Désactivable dans Réglages → Système, ou en retirant le service des services d'accessibilité de la TV.
 */
class ButtonService : AccessibilityService() {
    private var fired = false
    private val handler = Handler(Looper.getMainLooper())
    private val holdTimer = Runnable {
        fired = true
        openControlCenter()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean = try {
        handle(event)
    } catch (t: Throwable) {
        // Quoi qu'il arrive, la télécommande ne doit jamais être bloquée par ce service
        Log.w(TAG, "Touche ignorée", t)
        false
    }

    private fun handle(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_BACK) return false
        if (!getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("back_hold", true)) return false
        // Le filtre d'accessibilité ne voit que l'appui et le relâchement (pas les répétitions de l'appui long) :
        // un minuteur de 700 ms décide si l'appui est long.
        return when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount == 0) {
                    fired = false
                    handler.removeCallbacks(holdTimer)
                    handler.postDelayed(holdTimer, HOLD_MS)
                }
                // L'appui commence : on laisse passer (un appui court n'est jamais touché) ; les répétitions
                // éventuelles ne passent que si l'appui est devenu long, et sont alors avalées
                fired
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(holdTimer)
                if (fired) { fired = false; true } else false // pas de « Retour » en fin d'appui long
            }
            else -> false
        }
    }

    private fun openControlCenter() {
        Log.i(TAG, "Retour long : centre de contrôle")
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra("lights", true)
                    .putExtra("from_overlay", true)
            )
        }.onFailure { Log.w(TAG, "Ouverture impossible", it) }
    }

    companion object {
        private const val TAG = "TvHomeButton"
        private const val HOLD_MS = 700L
        const val COMPONENT = "fr.alban.tvhome/fr.alban.tvhome.ButtonService"
    }
}
