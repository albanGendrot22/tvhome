package fr.alban.tvhome

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var apps by mutableStateOf<List<TvApp>>(emptyList())
    private var sources by mutableStateOf<List<TvSource>>(emptyList())
    private var picks by mutableStateOf<Map<String, List<Pick>>>(emptyMap())

    // Les 3 dernières images du Top Shelf restent en mémoire, pour revenir sur une appli sans attendre
    private val pickCache = object : LinkedHashMap<String, Wallpaper>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Wallpaper>?) = size > 2
    }
    private var resume by mutableStateOf<List<ResumeItem>>(emptyList())
    private var others by mutableStateOf<Set<String>>(emptySet())
    private var hiddenApps by mutableStateOf<List<TvApp>>(emptyList())
    private var settingsSignal by mutableIntStateOf(0)
    private var sceneSignal by mutableIntStateOf(0)
    private var sceneKind by mutableIntStateOf(0)
    // Centre de contrôle ouvert par l'appui long sur Retour depuis une autre appli : à la fermeture on y retourne
    private var returnToPrevious = false
    private var homeSignal by mutableIntStateOf(0)
    private var lightsSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Le thème garde un fond noir pour la fenêtre de démarrage ; ensuite l'écran est entièrement peint par Compose,
        // le fond de la fenêtre ne ferait que remplir l'écran une fois de plus à chaque image
        window.setBackgroundDrawable(null)
        UiSettings.init(this)
        handleLightsIntent(intent)
        repairBackHold()
        // « Reprendre la lecture » : rechargée quand une lecture est notée, hors du thread principal
        lifecycleScope.launch {
            ContinueStore.changes.collect {
                resume = withContext(Dispatchers.IO) { ContinueStore.load(this@MainActivity) }
            }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val weather by rememberWeather()
                val backdropMode by UiSettings.backdropMode
                // Photo de nuit (New York) ou de jour (désert) selon le lever et le coucher du soleil
                val now by rememberNow()
                val night = weather?.isNight(now) ?: (now.hour < 7 || now.hour >= 20)
                val wallpaper by rememberWallpaper(this@MainActivity, backdropMode != UiSettings.BACKDROP_SOLID, night)
                // L'économiseur d'écran (StandBy) choisit sa photo avec cette valeur
                LaunchedEffect(night) { getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean("is_night", night).apply() }
                HomeScreen(
                    apps = apps,
                    wallpaper = wallpaper,
                    weather = weather,
                    homeSignal = homeSignal,
                    onLaunch = ::openApp,
                    onInfo = ::openInfo,
                    onReorder = { list ->
                        apps = list
                        Prefs.saveOrder(this@MainActivity, list)
                    },
                    onHide = { app ->
                        Prefs.hide(this@MainActivity, app.pkg)
                        apps = apps.filter { it.pkg != app.pkg }
                        hiddenApps = (hiddenApps + app).sortedBy { it.label.lowercase() }
                    },
                    onUnhideAll = {
                        Prefs.unhideAll(this@MainActivity)
                        reload()
                    },
                    otherPkgs = others,
                    hiddenApps = hiddenApps,
                    onUnhide = { app ->
                        Prefs.unhide(this@MainActivity, app.pkg)
                        reload()
                    },
                    onRename = { app, label ->
                        Prefs.rename(this@MainActivity, app.pkg, label)
                        reload()
                    },
                    onOther = { app, on ->
                        Prefs.setOther(this@MainActivity, app.pkg, on)
                        others = Prefs.other(this@MainActivity)
                    },
                    onResetLayout = {
                        Prefs.resetLayout(this@MainActivity)
                        reload()
                    },
                    settingsSignal = settingsSignal,
                    sceneSignal = sceneSignal,
                    sceneKind = sceneKind,
                    resume = resume,
                    onResume = { item -> apps.firstOrNull { it.pkg == item.pkg }?.let(::openApp) },
                    onResumeRemove = { item -> lifecycleScope.launch(Dispatchers.IO) { ContinueStore.remove(this@MainActivity, item.pkg) } },
                    onControlClosed = {
                        if (returnToPrevious) {
                            returnToPrevious = false
                            moveTaskToBack(true) // retour à l'appli qui était à l'écran (Netflix…)
                        }
                    },
                    sources = sources,
                    onSource = { src ->
                        if (!SourceRepository.open(this@MainActivity, src)) {
                            Toast.makeText(this@MainActivity, "Impossible d'ouvrir ${src.name}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onHideSource = { src ->
                        Prefs.hide(this@MainActivity, src.hideKey)
                        sources = sources.filter { it.id != src.id }
                    },
                    picks = picks,
                    loadPick = { p ->
                        val key = "${p.type}-${p.id}"
                        pickCache[key] ?: TrendingRepository.image(this@MainActivity, p)?.also { pickCache[key] = it }
                    },
                    options = HomeOptionsActions(
                        enableSafetyNet = {
                            adbTask("Filet de sécurité actif ✓") { LocalAdb.check(this@MainActivity) }
                        },
                        enableScreensaver = {
                            adbTask("Économiseur d'écran photos activé ✓") {
                                LocalAdb.enablePhotoScreensaver(this@MainActivity)
                            }
                        },
                        simulateCrash = {
                            throw RuntimeException("Plantage volontaire pour tester le filet de sécurité")
                        },
                        restoreGoogle = {
                            adbTask("Retour à l'accueil Google…") { LocalAdb.restoreGoogle(this@MainActivity) }
                        },
                        grantMediaAccess = {
                            adbTask("Détection lecture / pause autorisée ✓") { LocalAdb.allowMediaListener(this@MainActivity) }
                        },
                        setBackHold = { on ->
                            UiSettings.setBackHold(on)
                            if (on) adbTask("Retour long : centre de contrôle ✓") { LocalAdb.enableBackHold(this@MainActivity) }
                            else adbTask("Retour long désactivé") { LocalAdb.disableBackHold(this@MainActivity) }
                        },
                    ),
                    lightsSignal = lightsSignal,
                )
            }
        }
    }

    // Rechargé à chaque retour sur l'accueil : une app installée ou supprimée apparaît direct.
    override fun onResume() {
        super.onResume()
        reload()
    }

    /**
     * Réglages des lumières envoyés par ADB pendant la mise en place (ouvrir le panneau, choisir le salon
     * ou la scène "Musique"). Ne fait que choisir parmi les pièces et scènes existantes du pont.
     */
    private fun handleLightsIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("hue_salon")?.let { Lights.setSalon(this, it) }
        intent.getStringExtra("hue_music")?.let { Lights.setMusicScene(this, it) }
        if (intent.getBooleanExtra("lights", false)) {
            returnToPrevious = intent.getBooleanExtra("from_overlay", false)
            lightsSignal++
        }
        // Réglages envoyables par ADB (mesures) : --ez anim_reduced true
        if (intent.hasExtra("anim_reduced")) UiSettings.setReducedAnimations(intent.getBooleanExtra("anim_reduced", false))
        // --ei backdrop 0|1|2 : Top Shelf dynamique / photo fixe / fond uni sombre
        if (intent.hasExtra("backdrop")) UiSettings.setBackdropMode(intent.getIntExtra("backdrop", 0))
        // --ez glass_band true : bande de verre dépoli sous la première rangée
        if (intent.hasExtra("glass_band")) UiSettings.setGlassBand(intent.getBooleanExtra("glass_band", false))
        // --ez nav_sound true : son de navigation (sons système du focus)
        if (intent.hasExtra("nav_sound")) UiSettings.setNavSound(intent.getBooleanExtra("nav_sound", false))
        // --ez resume_demo true : une lecture fictive pour essayer la rangée « Reprendre la lecture »
        // --ez resume_clear true : vide cette rangée
        if (intent.getBooleanExtra("resume_demo", false)) ContinueStore.demo(this)
        if (intent.getBooleanExtra("resume_clear", false)) {
            getSharedPreferences("resume", MODE_PRIVATE).edit().clear().apply()
            ContinueStore.changes.value++
        }
        // --ez scene true : active la scène (désactivée par défaut) ; --ei scene_show 1|2 : affiche le mode Art (1)
        // ou la scène (2) tout de suite
        if (intent.hasExtra("scene")) UiSettings.setScene(intent.getBooleanExtra("scene", false))
        if (intent.hasExtra("scene_show")) {
            sceneKind = intent.getIntExtra("scene_show", 2)
            sceneSignal++
        }
        // --ez settings true : ouvre les réglages de l'accueil (captures d'écran)
        if (intent.getBooleanExtra("settings", false)) settingsSignal++
    }

    /**
     * Android retire le service d'accessibilité d'une appli à chaque mise à jour de celle-ci. Si le raccourci
     * « Retour long » est voulu mais que le service n'est plus actif, on le réactive par l'ADB local, sans bruit.
     */
    private fun repairBackHold() {
        if (!UiSettings.backHold.value) return
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        if (enabled.contains("ButtonService")) return
        lifecycleScope.launch(Dispatchers.IO) { LocalAdb.enableBackHold(this@MainActivity) }
    }

    private fun reload() {
        lifecycleScope.launch {
            apps = AppRepository.load(this@MainActivity)
            others = Prefs.other(this@MainActivity)
            hiddenApps = AppRepository.loadHidden(this@MainActivity)
            picks = TrendingRepository.load(this@MainActivity, apps.map { it.pkg }.toSet())
            // Téléchargement des images en tâche de fond : au moment du focus, il n'y a plus qu'à les lire sur le disque
            // (seulement si le fond suit la sélection : sinon seuls les titres servent)
            if (UiSettings.backdropMode.value == UiSettings.BACKDROP_SHELF) TrendingRepository.prefetch(this@MainActivity, picks)
        }
        lifecycleScope.launch { sources = SourceRepository.load(this@MainActivity) }
    }

    /** Lance une commande ADB locale en arrière-plan, puis affiche le résultat. */
    private fun adbTask(success: String, block: () -> Result<String>) {
        Toast.makeText(this, "Connexion à la TV…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { block() }
            val msg = result.fold(
                onSuccess = { success },
                onFailure = { "Échec : ${it.message ?: it.javaClass.simpleName}" },
            )
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }

    // Appui sur Home alors qu'on est déjà sur l'accueil
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra("hue_salon") || intent.hasExtra("hue_music") || intent.hasExtra("lights") || intent.hasExtra("anim_reduced") || intent.hasExtra("backdrop") || intent.hasExtra("glass_band") || intent.hasExtra("nav_sound") || intent.hasExtra("settings") || intent.hasExtra("resume_demo") || intent.hasExtra("resume_clear") || intent.hasExtra("scene") || intent.hasExtra("scene_show")) {
            handleLightsIntent(intent)
            return
        }
        homeSignal++
    }

    private fun openApp(app: TvApp) {
        try {
            startActivity(app.intent)
            Prefs.pushRecent(this, app.pkg)
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible d'ouvrir ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInfo(app: TvApp) {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
