package fr.alban.tvhome

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Les commandes que l'appli lance sur la TV via son propre accès ADB. */
object LocalAdb {
    private const val TAG = "TvHomeAdb"
    const val GOOGLE_LAUNCHER = "com.google.android.tvlauncher"

    /** Exécute les commandes une par une. À appeler hors du thread principal. */
    fun run(ctx: Context, vararg commands: String): Result<String> = runCatching {
        val adb = AdbClient(ctx.filesDir.resolve("adb"))
        try {
            adb.connect()
            commands.joinToString("\n") { adb.shell(it).trim() }
        } finally {
            adb.close()
        }
    }.onFailure { Log.w(TAG, "ADB local en échec", it) }

    /** Vérifie que l'accès fonctionne ; la toute première fois, la TV demande l'autorisation. */
    fun check(ctx: Context): Result<String> = run(ctx, "echo ok")

    /**
     * Rend la main au launcher Google et met TvHome en sommeil.
     * Tout part en une seule commande : elle continue côté TV même si notre appli s'arrête en route.
     */
    fun restoreGoogle(ctx: Context): Result<String> = run(
        ctx,
        "pm enable $GOOGLE_LAUNCHER && pm disable-user --user 0 ${ctx.packageName}; " +
            "am start -a android.intent.action.MAIN -c android.intent.category.HOME",
    )

    /**
     * Autorise TvHome à voir si une appli est en lecture ou en pause (sessions média).
     * Android ne le permet qu'à un "écouteur de notifications" : notre MediaListener, qui n'en lit aucune.
     */
    fun allowMediaListener(ctx: Context): Result<String> = run(
        ctx,
        "cmd notification allow_listener ${ctx.packageName}/${MediaListener::class.java.name}",
    )

    /**
     * Active le service « Retour long » dans les services d'accessibilité, en gardant ceux déjà actifs.
     * Une seule commande, lue côté TV.
     */
    fun enableBackHold(ctx: Context): Result<String> = run(
        ctx,
        "cur=\$(settings get secure enabled_accessibility_services); " +
            "case \"\$cur\" in *ButtonService*) ;; null|\"\") settings put secure enabled_accessibility_services ${ButtonService.COMPONENT};; " +
            "*) settings put secure enabled_accessibility_services \"\$cur:${ButtonService.COMPONENT}\";; esac; " +
            "settings put secure accessibility_enabled 1; settings get secure enabled_accessibility_services",
    )

    /** Retire le service « Retour long » des services d'accessibilité. */
    fun disableBackHold(ctx: Context): Result<String> = run(
        ctx,
        "cur=\$(settings get secure enabled_accessibility_services); " +
            "new=\$(echo \"\$cur\" | sed -e 's#${ButtonService.COMPONENT}##' -e 's#::*#:#g' -e 's#^:##' -e 's#:\$##'); " +
            "if [ -z \"\$new\" ]; then settings delete secure enabled_accessibility_services; " +
            "else settings put secure enabled_accessibility_services \"\$new\"; fi; settings get secure enabled_accessibility_services",
    )

    /** Déclare notre économiseur d'écran photos comme écran de veille de la TV. */
    fun enablePhotoScreensaver(ctx: Context): Result<String> = run(
        ctx,
        "settings put secure screensaver_enabled 1",
        "settings put secure screensaver_components ${ctx.packageName}/.PhotoDream",
        "settings put secure screensaver_default_component ${ctx.packageName}/.PhotoDream",
    )
}

/**
 * Compte les plantages. Trois en cinq minutes : l'accueil est considéré comme cassé
 * et on demande au processus de secours de rendre la main au launcher Google.
 */
object CrashGuard {
    private const val PREFS = "guard"
    private const val KEY = "crashes"
    private const val WINDOW_MS = 5 * 60_000L
    private const val LIMIT = 3

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { onCrash(app) }
            if (previous != null) previous.uncaughtException(thread, error)
            else {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            }
        }
    }

    private fun onCrash(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val recent = prefs.getString(KEY, "").orEmpty()
            .split(',').mapNotNull { it.toLongOrNull() }
            .filter { now - it < WINDOW_MS } + now
        if (recent.size >= LIMIT) {
            prefs.edit().remove(KEY).commit()
            ctx.sendBroadcast(Intent(ctx, RescueReceiver::class.java).setAction(RescueReceiver.ACTION))
        } else {
            prefs.edit().putString(KEY, recent.joinToString(",")).commit()
        }
    }

    fun reset(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}

/** Tourne dans un processus à part (":rescue"), qui survit aux plantages de l'accueil. */
class RescueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                LocalAdb.restoreGoogle(app)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION = "fr.alban.tvhome.RESCUE"
    }
}

class TvHomeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Le gardien ne surveille que l'accueil, pas le processus de secours lui-même
        if (Application.getProcessName() == packageName) {
            CrashGuard.install(this)
            Lights.install(this)
        }
    }
}
