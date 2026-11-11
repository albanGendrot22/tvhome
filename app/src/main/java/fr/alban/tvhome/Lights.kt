package fr.alban.tvhome

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class HueStatus { UNPAIRED, OK, UNREACHABLE }

/**
 * Les lumières Hue côté TvHome, en commande manuelle seulement : réglages (dans les préférences privées de l'appli,
 * jamais dans le code), état du pont, scènes. Aucun automatisme : rien ne bouge tout seul quand on lance une appli,
 * une lecture ou l'économiseur d'écran.
 *
 * Tout passe par une seule file de commandes : jamais deux ordres en même temps au pont,
 * et rien ne bloque l'accueil si le pont ne répond pas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object Lights {
    private val queue = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _status = MutableStateFlow(HueStatus.UNPAIRED)
    val status: StateFlow<HueStatus> = _status

    private fun sp(ctx: Context) = ctx.getSharedPreferences("hue", Context.MODE_PRIVATE)

    fun config(ctx: Context): HueConfig? {
        val p = sp(ctx)
        val ip = p.getString("ip", null) ?: return null
        val key = p.getString("key", null) ?: return null
        val pin = p.getString("pin", null) ?: return null
        return HueConfig(ip, key, pin)
    }

    fun salon(ctx: Context): String? = sp(ctx).getString("salon", null)
    fun setSalon(ctx: Context, roomId: String) = sp(ctx).edit().putString("salon", roomId).apply()
    fun musicScene(ctx: Context): String? = sp(ctx).getString("music_scene", null)
    fun setMusicScene(ctx: Context, sceneId: String) = sp(ctx).edit().putString("music_scene", sceneId).apply()

    fun savePairing(ctx: Context, ip: String, key: String, pin: String) {
        sp(ctx).edit().putString("ip", ip).putString("key", key).putString("pin", pin).apply()
        _status.value = HueStatus.OK
    }

    /** Vérifie une fois que le pont répond (au démarrage et à l'ouverture du panneau). Jamais en boucle. */
    fun refreshStatus(ctx: Context) {
        val app = ctx.applicationContext
        queue.launch {
            val c = config(app) ?: run { _status.value = HueStatus.UNPAIRED; return@launch }
            if (HueBridge.reachable(c)) { _status.value = HueStatus.OK; return@launch }
            // L'adresse du pont a peut-être changé : on le recherche, l'empreinte du certificat dira si c'est bien lui
            val ip = HueBridge.discover(app)
            val moved = ip?.takeIf { it != c.ip }?.let { c.copy(ip = it) }
            if (moved != null && HueBridge.reachable(moved)) {
                sp(app).edit().putString("ip", moved.ip).apply()
                _status.value = HueStatus.OK
            } else {
                _status.value = HueStatus.UNREACHABLE
            }
        }
    }

    /** Pastille « Musique » du panneau : la scène choisie pour la musique (appui long sur une scène). */
    fun music(ctx: Context) {
        val app = ctx.applicationContext
        queue.launch {
            val c = config(app) ?: return@launch
            val scene = musicScene(app) ?: return@launch
            HueBridge.recallScene(c, scene)
        }
    }

    /** Rappelle une scène existante du pont (sans jamais la modifier). */
    fun recall(ctx: Context, sceneId: String) {
        val app = ctx.applicationContext
        queue.launch { config(app)?.let { HueBridge.recallScene(it, sceneId) } }
    }

    /** À appeler une fois, au démarrage du processus de l'accueil : une vérification du pont, rien d'autre. */
    fun install(app: Context) {
        refreshStatus(app)
    }
}
