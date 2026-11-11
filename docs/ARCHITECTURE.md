# Architecture

Package `fr.alban.tvhome` (`app/src/main/java`).

| Fichier | Rôle |
|---|---|
| `MainActivity.kt` | Activité d'accueil, état global, intentions de réglage (adb), retour à l'appli précédente |
| `HomeScreen.kt` | Écran Compose : Top Shelf, grille, capsule de verre, menus d'appli, dossier |
| `Menus.kt` | `GlassScreen` / `GlassCard` (faux verre), réglages façon tvOS, menu d'appli, renommer, dossier |
| `ControlCenter.kt` | Centre de contrôle : Wi-Fi, Bluetooth, son, mémoire, minuteur de veille, Ne pas déranger, sources |
| `LightsPanel.kt`, `Lights.kt`, `Hue.kt`, `MediaListener.kt` | Lumières Philips Hue (API locale v2, pont appairé une fois) |
| `ButtonService.kt` | Service d'accessibilité : appui long sur Retour = centre de contrôle |
| `Apps.kt` | Liste des applis, ordre, masquage, noms, dossier « Autres applis » |
| `Wallpapers.kt` | Photos jour / nuit, flou précalculé, fabrication de la capsule |
| `Weather.kt`, `Trending.kt`, `Sources.kt` | Météo Open-Meteo, titres tendance TMDB, entrées HDMI |
| `ContinueWatching.kt` | « Reprendre la lecture » : mémorise les lectures publiées par les applis (sessions média) |
| `Scene.kt`, `PhotoDream.kt` | Mode Art, scène (photo et horloge géante, désactivée par défaut), économiseur StandBy |
| `NavSound.kt` | Sons de navigation du système (`FX_FOCUS_NAVIGATION_*`) |
| `UiSettings.kt`, `Config.kt` | Réglages modifiables / constantes de configuration |
| `Safety.kt`, `AdbClient.kt` | Filet de sécurité et client ADB local (127.0.0.1:5555) |

## Réglages par adb (mesures et essais)
```
adb shell am start -n fr.alban.tvhome/.MainActivity --ez anim_reduced true --ei backdrop 1 --ez glass_band true --ez nav_sound false
adb shell am start -n fr.alban.tvhome/.MainActivity --ez settings true     # ouvre les réglages
adb shell am start -n fr.alban.tvhome/.MainActivity --ez lights true       # ouvre le centre de contrôle
```
```
adb shell am start -n fr.alban.tvhome/.MainActivity --ez scene true --ei scene_show 2    # scène (1 = mode Art)
adb shell am start -n fr.alban.tvhome/.MainActivity --ez resume_demo true                # lecture fictive
```
La ligne `adb logcat -d -s TvHome:I` confirme l'état actif.

## Service « Retour long »
`ButtonService` est un service d'accessibilité qui ne reçoit que les touches. Android le désactive à chaque
mise à jour de l'appli : au démarrage, TvHome le réactive par l'ADB local (la TV demande une fois d'autoriser
la clé : cocher « Toujours autoriser »). Les touches envoyées par `adb shell input` ne passent pas par ce service :
il faut la vraie télécommande pour l'essayer.

## Sécurité
- Le filet de sécurité réactive le launcher d'origine après 3 plantages en 5 minutes (processus `:rescue`).
- Ne jamais désactiver les paquets `com.mediatek.*`, `com.vestel.tv.*`, les services Google de base, ni Chromecast.
- Les secrets (jeton TMDB, coordonnées) sont dans `local.properties`, jamais dans le code.
