@file:OptIn(ExperimentalTvMaterial3Api::class)

package fr.alban.tvhome

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ExperimentalTvMaterial3Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Adapté de tvOS Launcher (techgenius1), Apache 2.0 : contenu du centre de contrôle
// (Wi-Fi, Bluetooth, son, mémoire, minuteur de veille, Ne pas déranger, sources HDMI, réglages).

/**
 * Centre de contrôle : le panneau de droite (celui des Lumières) avec, au-dessus des pièces Hue, les réglages
 * rapides de la TV. Tout est lu à l'ouverture, rien n'est interrogé en boucle ; fermé, il n'existe pas.
 */
@Composable
fun ControlCenter(
    glass: () -> ImageBitmap?,
    sources: List<TvSource>,
    onSource: (TvSource) -> Unit,
    apps: List<TvApp>,
    onLaunch: (TvApp) -> Unit,
    onArt: () -> Unit,
    onScene: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    LightsPanel(glass, onDismiss) { first ->
        ControlRows(first, sources, onSource, apps, onLaunch, onArt, onScene, onSettings, onDismiss)
    }
}

@Composable
private fun ColumnScope.ControlRows(
    first: FocusRequester,
    sources: List<TvSource>,
    onSource: (TvSource) -> Unit,
    apps: List<TvApp>,
    onLaunch: (TvApp) -> Unit,
    onArt: () -> Unit,
    onScene: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun open(action: String) {
        runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        onClose()
    }

    // Wi-Fi et Bluetooth : l'état se lit, le réglage se fait dans les paramètres de la TV
    MenuRow("Wi-Fi", Modifier.focusRequester(first), value = network(ctx)) { open(Settings.ACTION_WIFI_SETTINGS) }
    MenuRow("Bluetooth", value = bluetooth()) { open(Settings.ACTION_BLUETOOTH_SETTINGS) }

    // Son : gauche / droite règlent le volume, OK coupe ou remet le son
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    fun volumeText(): String {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) "Muet" else "${audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max} %"
    }
    var volume by remember { mutableStateOf(volumeText()) }
    MenuRow(
        "Son",
        Modifier.onPreviewKeyEvent { e ->
            val dir = when (e.key) {
                Key.DirectionLeft -> AudioManager.ADJUST_LOWER
                Key.DirectionRight -> AudioManager.ADJUST_RAISE
                else -> return@onPreviewKeyEvent false
            }
            if (e.type == KeyEventType.KeyDown) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
                volume = volumeText()
            }
            true
        },
        value = "◂ $volume ▸",
    ) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        volume = volumeText()
    }

    // Mémoire : mémoire libre, OK libère les applis en arrière-plan
    var memory by remember { mutableStateOf("${Memory.freeMb(ctx)} Mo libres") }
    MenuRow("Libérer la mémoire", value = memory) {
        scope.launch {
            memory = "…"
            val freed = withContext(Dispatchers.Default) { Memory.clean(ctx) }
            memory = "+$freed Mo → ${Memory.freeMb(ctx)} Mo"
        }
    }

    // Applis récentes : les dernières lancées depuis l'accueil, OK les rouvre
    val recents = remember { Prefs.recent(ctx).mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } }.take(4) }
    if (recents.isNotEmpty()) {
        Text("Applis récentes", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        recents.forEach { app -> MenuRow(app.label) { onClose(); onLaunch(app) } }
    }

    // Minuteur de veille : OK passe au palier suivant
    var sleep by remember { mutableStateOf(SleepTimer.label(ctx)) }
    MenuRow("Minuteur de veille", value = sleep) {
        SleepTimer.next(ctx)
        sleep = SleepTimer.label(ctx)
    }

    // Ne pas déranger : l'accès se donne une fois par ADB local, comme le filet de sécurité
    var dnd by remember { mutableStateOf(DoNotDisturb.label(ctx)) }
    MenuRow("Ne pas déranger", value = dnd) {
        scope.launch {
            dnd = "…"
            withContext(Dispatchers.IO) { DoNotDisturb.toggle(ctx) }
            dnd = DoNotDisturb.label(ctx)
        }
    }

    // Sources HDMI
    sources.forEach { src ->
        MenuRow(src.name, value = src.subtitle) { onClose(); onSource(src) }
    }

    // Mode Art (la photo seule) et scène (photo et horloge géante, si activée dans Apparence)
    MenuRow("Mode Art") { onClose(); onArt() }
    val sceneOn by UiSettings.scene
    if (sceneOn) MenuRow("Scène : paysage et horloge") { onClose(); onScene() }

    // Réglages rapides de l'accueil
    val reduced by UiSettings.reducedAnimations
    val backdrop by UiSettings.backdropMode
    val navSound by UiSettings.navSound
    MenuRow("Animations", value = if (reduced) "Réduites" else "Normales") { UiSettings.setReducedAnimations(!reduced) }
    MenuRow(
        "Fond",
        value = when (backdrop) {
            UiSettings.BACKDROP_PHOTO -> "Photo fixe"
            UiSettings.BACKDROP_SOLID -> "Uni sombre"
            else -> "Top Shelf"
        },
    ) { UiSettings.setBackdropMode((backdrop + 1) % 3) }
    MenuRow("Son de navigation", value = if (navSound) "Activé" else "Désactivé") { UiSettings.setNavSound(!navSound) }
    MenuRow("Tous les réglages…", chevron = true) { onClose(); onSettings() }
}

private fun network(ctx: Context): String {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Hors ligne"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Connecté"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Connecté"
    }
}

@Suppress("DEPRECATION")
private fun bluetooth(): String {
    val a = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull() ?: return "Indisponible"
    return if (runCatching { a.isEnabled }.getOrDefault(false)) "Activé" else "Désactivé"
}

/** Mémoire : combien il en reste, et un nettoyage des applis en arrière-plan (sans toucher au système). */
object Memory {
    private val keep = listOf("com.mediatek", "com.vestel", "com.google.android.gms", "com.google.android.gsf", "com.android", "android")

    fun freeMb(ctx: Context): Long {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return info.availMem / (1024 * 1024)
    }

    /** Arrête les processus en arrière-plan des applis installées, sauf nous et le système. Rend les Mo gagnés. */
    fun clean(ctx: Context): Long {
        val before = freeMb(ctx)
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = ctx.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        pm.queryIntentActivities(query, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { pkg -> pkg != ctx.packageName && keep.none { pkg == it || pkg.startsWith("$it.") } }
            .forEach { runCatching { am.killBackgroundProcesses(it) } }
        Thread.sleep(800)
        return (freeMb(ctx) - before).coerceAtLeast(0)
    }
}

/** Minuteur de veille : une alarme qui met la TV en veille (touche Veille envoyée par l'ADB local). */
object SleepTimer {
    private val steps = listOf(0, 15, 30, 60, 90)
    private const val PREFS = "sleep"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun label(ctx: Context): String {
        val at = sp(ctx).getLong("at", 0L)
        val left = ((at - System.currentTimeMillis()) / 60_000L).toInt() + 1
        return if (at > System.currentTimeMillis()) "dans $left min" else "Désactivé"
    }

    /** Passe au palier suivant ; après le dernier, le minuteur s'éteint. */
    fun next(ctx: Context) {
        val at = sp(ctx).getLong("at", 0L)
        val left = if (at > System.currentTimeMillis()) ((at - System.currentTimeMillis()) / 60_000L).toInt() + 1 else 0
        val cur = steps.indexOfLast { it <= left }.coerceAtLeast(0)
        val minutes = steps[(cur + 1) % steps.size]
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, SleepReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        if (minutes == 0) {
            sp(ctx).edit().putLong("at", 0L).apply()
            return
        }
        val at2 = System.currentTimeMillis() + minutes * 60_000L
        sp(ctx).edit().putLong("at", at2).apply()
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at2, pi)
    }
}

class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        app.getSharedPreferences("sleep", Context.MODE_PRIVATE).edit().putLong("at", 0L).apply()
        val pending = goAsync()
        Thread {
            // KEYCODE_SLEEP : la TV passe en veille
            runCatching { LocalAdb.run(app, "input keyevent KEYCODE_SLEEP") }
            pending.finish()
        }.start()
    }
}

/** Ne pas déranger : filtre « alarmes seulement ». L'accès à la politique se donne une fois par ADB local. */
object DoNotDisturb {
    private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun label(ctx: Context): String {
        val n = nm(ctx)
        return when {
            !n.isNotificationPolicyAccessGranted -> "À autoriser"
            n.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
                n.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> "Désactivé"
            else -> "Activé"
        }
    }

    /** À appeler hors du thread principal. */
    suspend fun toggle(ctx: Context) {
        val n = nm(ctx)
        if (!n.isNotificationPolicyAccessGranted) {
            LocalAdb.run(ctx, "cmd notification allow_dnd ${ctx.packageName}")
            delay(500)
        }
        if (!n.isNotificationPolicyAccessGranted) return
        val on = n.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            n.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        n.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_ALARMS,
        )
        delay(300)
    }
}
