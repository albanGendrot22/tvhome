package fr.alban.tvhome

/** Les réglages qu'on a des chances de vouloir changer, au même endroit. */
object Config {
    // Météo (Open-Meteo, sans clé d'API). La position vient de local.properties (weather.lat / weather.lon)
    val LATITUDE: Double = BuildConfig.WEATHER_LAT
    val LONGITUDE: Double = BuildConfig.WEATHER_LON

    // Tuile "TV" pour l'antenne : désactivée, la TNT n'est pas configurée sur cette TV
    const val SHOW_ANTENNA = false

    // Interrupteur du Top Shelf (false = désactivé, pour les tests de performance)
    const val TOP_SHELF_ENABLED = true

    // Voile de nuit (assombrit l'accueil le soir) : désactivé, invisible à l'écran et coûteux pour le GPU
    const val NIGHT_VEIL_ENABLED = false

    // Durée des fondus du Top Shelf (photo de fond + texte), en ms
    const val SHELF_FADE_MS = 600

    // Top Shelf : jeton TMDB ("Jeton d'accès en lecture à l'API"), lu dans local.properties (tmdb.token).
    // Vide = pas de titres tendances (le reste de l'accueil fonctionne). Jamais écrit dans le code.
    val TMDB_TOKEN: String = BuildConfig.TMDB_TOKEN

    // Grille façon tvOS
    // Adapté de tvOS Launcher (techgenius1), Apache 2.0 : mesures tvOS 26 ramenées à notre grille de 960 dp :
    // cartes 124×74, espace 26, marge 43, zoom au focus 1,2 : six cartes par rangée
    const val COLUMNS = 6
    const val CARD_ASPECT = 248f / 148f
    const val SIDE_PAD_DP = 43f
    const val GAP_DP = 26f
    const val FOCUS_SCALE = 1.13f  // 1,2 chez tvOS Launcher ; gardé à 1,13 (le banc est trop bruité pour trancher, voir RAPPORT-DA)

    // Ordre de départ, avant que tu ne réorganises toi-même à la télécommande
    val DEFAULT_ORDER = listOf(
        "com.netflix.ninja",
        "com.apple.atve.androidtv.appletv",
        "com.google.android.youtube.tv",
        "com.disney.disneyplus",
        "com.amazon.amazonvideo.livingroom",
        "com.canal.android.canal",
        "com.spotify.tv.android",
    )
}
