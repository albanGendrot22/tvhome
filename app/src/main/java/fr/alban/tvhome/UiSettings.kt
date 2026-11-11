package fr.alban.tvhome

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateOf

/**
 * Réglages de l'accueil modifiables dans les options. Chaque valeur est un état Compose, mais elle n'est lue
 * que par les écrans qui en ont besoin : un changement ne recompose rien d'autre.
 */
object UiSettings {
    private var sp: SharedPreferences? = null

    /** Animations réduites : changements d'image instantanés, aucun fondu ni glissement. */
    // Par défaut (choix d'Alban du 25/10, après mesures) : animations réduites + photo fixe
    val reducedAnimations = mutableStateOf(true)

    /** Fond d'accueil : 0 = Top Shelf dynamique, 1 = photo fixe, 2 = fond uni sombre. */
    val backdropMode = mutableStateOf(BACKDROP_PHOTO)

    /**
     * Capsule de verre dépoli autour de la première rangée d'applis, fabriquée une fois (image prête à dessiner).
     * Activée par défaut : au banc alterné du 06/11, la phase 2 avec capsule ne fait pas pire que la fin de phase 1.
     */
    val glassBand = mutableStateOf(true)

    /**
     * Scène « Night Scenes » (paysage et horloge géante, image fixe) : désactivée par défaut. Activée, elle se lance
     * seule après 5 minutes sans touche sur l'accueil, ou depuis le centre de contrôle.
     */
    val scene = mutableStateOf(false)

    fun setScene(on: Boolean) {
        scene.value = on
        sp?.edit()?.putBoolean("scene", on)?.apply()
        logState("changement")
    }

    /** Appui long sur Retour = centre de contrôle (lu aussi par ButtonService, qui n'a pas accès à cet objet). */
    val backHold = mutableStateOf(true)

    fun setBackHold(on: Boolean) {
        backHold.value = on
        sp?.edit()?.putBoolean("back_hold", on)?.apply()
        logState("changement")
    }

    /** Son de navigation (sons système du focus). Désactivé par défaut tant que le bench ne l'a pas validé. */
    val navSound = mutableStateOf(false)

    fun setNavSound(on: Boolean) {
        navSound.value = on
        sp?.edit()?.putBoolean("nav_sound", on)?.apply()
        logState("changement")
    }

    fun init(ctx: Context) {
        if (sp != null) return
        sp = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).also {
            reducedAnimations.value = it.getBoolean("anim_reduced", true)
            backdropMode.value = it.getInt("backdrop", BACKDROP_PHOTO).coerceIn(BACKDROP_SHELF, BACKDROP_SOLID)
            glassBand.value = it.getBoolean("glass_band", true)
            navSound.value = it.getBoolean("nav_sound", false)
            backHold.value = it.getBoolean("back_hold", true)
            scene.value = it.getBoolean("scene", false)
        }
        logState("demarrage")
    }

    fun setGlassBand(on: Boolean) {
        glassBand.value = on
        sp?.edit()?.putBoolean("glass_band", on)?.apply()
        logState("changement")
    }

    fun setBackdropMode(mode: Int) {
        backdropMode.value = mode.coerceIn(BACKDROP_SHELF, BACKDROP_SOLID)
        sp?.edit()?.putInt("backdrop", backdropMode.value)?.apply()
        logState("changement")
    }

    /** Une ligne dans le journal pour vérifier un réglage sans y toucher : adb logcat -d | findstr TvHome */
    private fun logState(quand: String) {
        Log.i("TvHome", "anim=${if (reducedAnimations.value) "reduced" else "normal"} backdrop=${backdropMode.value} glass=${glassBand.value} navsound=${navSound.value} scene=${scene.value} ($quand)")
    }

    const val BACKDROP_SHELF = 0
    const val BACKDROP_PHOTO = 1
    const val BACKDROP_SOLID = 2

    fun setReducedAnimations(on: Boolean) {
        reducedAnimations.value = on
        sp?.edit()?.putBoolean("anim_reduced", on)?.apply()
        logState("changement")
    }
}
