# Mission « DA tvOS Launcher » — rapport (02/11/2026)

Référence : config par défaut (fond 1 + réduit) = 19 / 19 images ratées, 99e 20–30 ms.
Règle d'or : > 2 % d'images ratées ou > 34 ms au 99e → annulé ou option désactivée.
Sauvegarde de phase : `backup\2026-11-02-avant-da`.

## Phase 0 — État (en cours)

### Mise en place
- Dépôt récupéré en ZIP (git n'est pas installé) dans `C:\TvHome\ref\tvos-launcher` (Apache 2.0, lecture seule).
- Compilé dans une COPIE (`C:\TvHome\ref-build\tvos-launcher`), pas dans `ref`. Ajustements de build :
  SDK android-35 + build-tools 35 (le 34 n'est pas installé et son d8 plante), JBR 21.
- Une seule modif de code pour que le bench soit valide : mise à jour automatique GitHub désactivée par défaut
  (`AppUpdater.java`), sinon une boîte de dialogue peut voler le focus pendant la mesure.
- APK : `ref-build\tvos-launcher\build\tvos-home.apk` (6,7 Mo, signé clé debug).
- `bench\outils\bench-launcher.ps1` accepte maintenant la cible `tvos` (installe, saute l'assistant, bench,
  puis désinstalle et remet TvHome).

### 0b — Bench tvOS Launcher ×3 (05/11/2026) : FAIT
Fichiers : `bench\tvos-2026-11-05-1.txt`, `-2.txt`, `-3.txt` (le bench 2 a été relancé après une coupure de session ;
le 1 est complet, le 2 et le 3 sont repartis de zéro). Preuve : ligne « Ecran d'accueil actif » = tvOS Launcher avant chaque bench.
tvOS Launcher désinstallé, TvHome remis comme accueil (vérifié).

| Version | Images ratées | % d'images ratées | 99e (ms) | Images dessinées | Démarrage à froid | Mémoire (PSS) |
|---|---|---|---|---|---|---|
| Launcher Google | 28 / 74 / 74 | 2,0 / 5,1 / 5,1 % | 42 / 46 / 46 | ~1 430 | 1,51–1,56 s | 157 Mo |
| Plume | 11 / 12 / 20 | 2,1 / 2,3 / 3,7 % | 29 / 29 / 29 | ~530 | 0,40–0,42 s | – |
| **TvHome (config par défaut)** | **19 / 19** | **1,0 / 0,9 %** | **20 / 30** | ~2 000 | 0,51 s | 50–62 Mo |
| tvOS Launcher (compilé des sources) | 69 / 68 / 73 | 12,6 / 12,3 / 13,2 % | 81 / 81 / 85 | ~550 | 1,22–1,27 s | ~120 Mo |

Lecture : tvOS Launcher dessine peu d'images (~550) mais en rate 12–13 %, contre 1 % pour nous ; 99e à 81–85 ms
(le double de Google), démarrage 2,4× plus lent, mémoire 2× plus grande. Conclusion : on ne reprend PAS son code de rendu ;
on ne transpose que ses idées de menus / géométrie, avec nos règles de perf. (Le « 99e GPU 4950 ms » est un artefact de
mesure, présent aussi chez les autres.)

### 0a — Comparaison avec le profil CleanYourTV « jvc-android-tv-menlo »
Le profil dit : `fusion.android.tv.demo` reçoit la touche Input (`TV_INPUT_BUTTON`), à GARDER activé.
Notre `bench.ps1` (liste `$menage`) le désactive → à vérifier avec toi.

| Notre ménage | Profil menlo | Écart |
|---|---|---|
| com.vestel.vestelreporter | désactivé | ok |
| **fusion.android.tv.demo** | **KEEP (touche Input)** | **⚠ écart** |
| com.cltv.fast | absent du profil | à toi de voir |
| **com.mediatek.tv.freeviewplay** | « tout com.mediatek.* » en KEEP | **⚠ écart** (et contraire à notre règle CLAUDE.md) |
| com.google.android.play.games | désactivé | ok |
| com.google.android.feedback | désactivé | ok |
| com.android.printspooler | désactivé | ok |
| com.dsi.ant.service.socket | absent du profil | à toi de voir |
| com.google.android.katniss | désactivé | ok |
| com.google.android.tvrecommendations | désactivé (seulement si l'accueil tiers est Home) | ok |
| com.google.android.leanbacklauncher.partnercustomizer | le profil garde `com.mediatek.android.leanbacklauncher.partnercustomizer` (autre paquet, plante `mediatek.tv.service` au reboot) | pas le même paquet, mais à surveiller |

Le profil désactive aussi, et pas nous : speedup, smartcenter, partnersetup, onetimeinitializer, autoinstalls vestel,
youtube.tvmusic, videos, backupconfirm, sharedstoragebackup, wallpaperbackup, syncadapters.calendar,
providers.calendar, dynsystem, ctsshim, backdrop. (Option, non appliqué.)

À faire quand la TV répond : `pm list packages -d` pour voir ce qui est vraiment désactivé chez nous.

### 0a — Touche Input : testée par Alban le 05/11
La touche Input ouvre bien la liste des sources, alors que `fusion.android.tv.demo` est désactivé
(vérifié : il est dans `pm list packages -d`). Rien à réactiver, `bench.ps1` reste inchangé.
Écarts du tableau ci-dessus : gardés tels quels (`freeviewplay` est désactivé depuis longtemps et tout marche ;
à surveiller vu la règle `com.mediatek.*` de CLAUDE.md).

## En attente de ton feu vert pour la Phase 1
(perfs gratuites : fond de fenêtre, assombrissement dans le bitmap, bande de verre, sons système).

## Références visuelles d'Alban (06/11) — à intégrer en phase 2 « quand pertinent »
Captures : `C:\Users\alban\.claude\uploads\28207950-5992-44fb-9c03-548f383939a0\` (menu Réglages + accueil).
- Réglages : titre centré en haut ; à gauche grande icône arrondie + phrase d'aide ; à droite liste de pastilles
  (libellé à gauche, valeur + chevron à droite) ; ligne sélectionnée = blanc plein, texte sombre ; autres = verre translucide.
- Accueil : 1re rangée dans une capsule de verre très arrondie (= notre bande de verre, option 1c) ; tuiles aux
  coins arrondis ; nom de l'appli visible seulement sous la tuile sélectionnée ; fond paysage flou.
- Règle inchangée : faux verre précalculé, pas de flou temps réel, budget perf (≤ 2 % ratées, 99e ≤ 34 ms).

## Phase 1 — Perfs gratuites (06/11/2026)
Config mesurée : fond 1 + animations réduites (preuve logcat à chaque série : `bench\preuve-p1*.txt`).
Référence : 19 / 19 images ratées, 99e 20–30 ms. Sauvegardes : `backup\2026-11-05-avant-phase1`, `-apres-1a`.

| Essai | Images ratées | % | 99e (ms) | Verdict |
|---|---|---|---|---|
| 1a Fond de fenêtre supprimé (`setBackgroundDrawable(null)`, thème noir gardé pour le démarrage) | 17 / 14 | 0,9 / 0,7 % | 19 / 19 | **gardé** |
| 1b Assombrissement cuit dans les bitmaps (recadrage 16:9 + dégradés au chargement) | 42 / 33 | 2,0 / 1,6 % | 32 / 32 | **annulé** (pire que 1a) |
| 1c Bande de verre sous les applis (avec 1a) | 64 / 61 | 3,1 / 3,0 % | 32 / 32 | **option désactivée par défaut** (> 2 %) |
| 1d Son de navigation, sons système `FX_FOCUS_NAVIGATION_*` volume 0,6 (avec 1a) | 20 / 24 | 1,0 / 1,2 % | 21 / 20 | **gardé** (dans le budget), voir ci-dessous |

Remarques :
- 1a : léger gain, cohérent sur 2 benchs (17 / 14 contre 19 / 19, 99e 19 ms).
- 1b : le gain attendu ne vient pas ; le coût vient du recadrage / de la taille du bitmap net, pas du dégradé. Fichiers rétablis.
- 1c : la règle d'or l'exclut du défaut. Elle reste dans Options → « Verre dépoli sous les applis ».
- 1d : il n'y avait pas de SoundPool chez nous (jamais fait) : ajouté de zéro (`NavSound.kt`, aucune ressource embarquée).
  Réglage Options → « Son de navigation », **désactivé par défaut** (goût personnel, pas de perf) ; actif sur la TV pour test d'écoute.
  Mesuré avec le son actif pendant les 60 appuis. Je ne peux pas entendre le son : à confirmer à l'oreille.
- Code transposé de tvOS Launcher en phase 1 : aucun (NOTICE inchangée).

## À faire en fin de mission (demande d'Alban, 06/11)
- Supprimer tous les automatismes des lumières (Lights.kt : LightAuto, déclencheurs lecture / pause, ouverture d'appli ou
  de source, retour accueil, extinction après l'économiseur ; MediaListener ; options « Lumières Hue… » des automatismes).
  À confirmer au moment de le faire : le panneau manuel (pièces, scènes, luminosité) reste dans le centre de contrôle.
  Sauvegarde de `app\src` avant, puis bench de contrôle.
- Photos d'accueil : nuit.jpg (New York) et jour.jpg (désert) seulement ; anciens fonds dans `wallpapers-old` (TV) et `photos-tv\_anciens` (PC).

## Phase 2 — Direction artistique et menus (06/11/2026)
Sauvegardes : `backup\2026-11-06-avant-phase2` (fin de phase 1), `backup\2026-11-06-phase2-en-cours`.
Captures : `bench\captures\avant-phase2-*`, `p2-*`, `p2g-*`, `p2h-*`, `p2t-*` (essais à la télécommande).
APK : `bench\apk\` (A = fin phase 1, B = phase 2, C = phase 2 capsule par défaut). Script alterné : `bench\outils\ab.ps1`.

### Ce qui est fait
| Élément | Statut |
|---|---|
| 2d Géométrie tvOS 26 : 6 colonnes, cartes 248:148, marge 43, espace 26, zoom 1,13 (1,2 de tvOS non gardé) | **gardé** |
| Sources alignées sur la même grille | gardé |
| Nom d'appli supprimé sous les logos (choix d'Alban) | gardé |
| Ombre du titre / de l'heure sur toute la hauteur (plus de coupure nette au-dessus de la 1re rangée) | gardé |
| Capsule de verre dépoli autour de la 1re rangée : image fabriquée une fois (photo légèrement floue, voile clair, reflet, liseré, coins ronds) | **gardé, activée par défaut** (réglable : Apparence) |
| 2a Réglages façon tvOS (liste de pastilles à droite, icône et aide à gauche, pages Apparence / Applis / Système) | gardé |
| 2b Menu d'appli : Renommer, Déplacer, Ranger dans Autres applis, Masquer, Infos, Réinitialiser la disposition | gardé, testé à la télécommande |
| 2b Dossier « Autres applis » (tuile 2×2 en fin de grille) | gardé, testé |
| 2b Coffre « Applis masquées » (Réglages → Applis) avec « Réafficher » et « Tout réafficher » | gardé, testé |
| 2c Centre de contrôle (panneau de droite de 380 dp) : Wi-Fi, Bluetooth, Son, Libérer la mémoire, Minuteur de veille, Ne pas déranger, Sources HDMI, Animations / Fond / Son de navigation, puis Lumières | gardé, testé (mémoire +19 Mo, minuteur 15 min posé puis annulé, Ne pas déranger activé puis remis) |
| Photos d'accueil : nuit.jpg (New York, vue en hauteur) / jour.jpg (désert), choisies selon lever et coucher du soleil | gardé |

### Perf : ce que disent les chiffres (à lire avec prudence)
Le banc est devenu très bruité cet après-midi : la même version (fin de phase 1) a donné 20 / 24 à 11h40,
puis 24, 83, 70, 55 entre 13h et 13h40. Les mesures de 11h45 à 13h20 (71 / 61, 67 / 26, 71 / 57, 66 / 32, 75 / 46)
ne sont donc pas attribuables à un changement de code. Mesures alternées, même période :

| Version | Images ratées | 99e |
|---|---|---|
| A fin de phase 1 | 70 · 55 (et 24 · 83 juste avant) | 32 · 32 ms |
| B phase 2 (6 colonnes + capsule) | 27 · 56 | 20 · 34 ms |

Conclusion : la phase 2 n'est pas plus lente que la fin de phase 1. À confirmer en phase 4 (alterné ×3 minimum).
Les menus n'existent que lorsqu'ils sont ouverts : coût nul sur l'accueil.
Essais annulés : assombrissement cuit dans le bitmap (1b, pire), zoom 1,2 (non mesurable proprement), bande de
verre dépoli en temps réel (3 % d'images ratées, remplacée par la capsule fabriquée).

### Réserves
- Le son du TV est actuellement « Muet » d'après le système (dumpsys audio) ; le centre de contrôle l'affiche. Je n'ai pas touché au volume.
- Renommer : le clavier de la TV s'ouvre quand on choisit la ligne « Nom » ; la touche ✓ du clavier valide.
- Minuteur de veille : envoie la touche Veille par l'ADB local à l'heure dite. Armé puis annulé pendant les tests, jamais déclenché.
- Ne pas déranger : l'accès est donné une fois par l'ADB local (`cmd notification allow_dnd`).
- Wi-Fi et Bluetooth : lecture de l'état, le réglage s'ouvre dans les paramètres de la TV (une appli ne peut plus les changer seule sur Android 11).
- À faire en fin de mission : suppression des automatismes de lumières (voir plus haut).

## Phase 3 — Fonctions (06/11/2026)
Sauvegarde : `backup\2026-11-06-avant-phase3`. APK : `bench\apk\D-phase3.apk`. Captures : `bench\captures\p3*`.

| Élément | Statut |
|---|---|
| 3a Appui long sur Retour = centre de contrôle, partout (service d'accessibilité `ButtonService`) | **installé, à valider à la télécommande** (les touches `adb input` ne passent pas par le filtre d'accessibilité) |
| 3a Retour à l'appli précédente à la fermeture du centre de contrôle (`moveTaskToBack`) | gardé, à valider avec le point précédent |
| 3a Réglage « Appui long sur Retour » (Réglages → Système) ; réactivation automatique du service au démarrage | gardé, voir réserve ADB |
| 3b Rangée « Reprendre la lecture » (vignette, titre, appli, avancement ; OK relance l'appli, appui long retire) | gardé, testé avec une lecture fictive ; se remplira avec les vraies lectures (Netflix publie lecture / pause) |
| 3c Applis récentes (4 dernières, dans le centre de contrôle) et « Libérer la mémoire » | gardé |
| 3d Économiseur StandBy (photo jour / nuit fixe, horloge géante, décalage à la minute) | gardé, testé, puis économiseur Google remis (à activer : Réglages → Économiseur d'écran) |
| 3d Couleur signature par appli (liseré de la tuile sélectionnée, tirée du logo une fois au chargement) | gardé |
| 3d Mode Art (la photo seule, une touche pour sortir) | gardé (centre de contrôle) |
| 3e Scène « Night Scenes » (photo et horloge géante, image fixe, lancement seule après 5 min sans touche) | gardé, **désactivée par défaut** (Réglages → Apparence) |

Perf (banc alterné, capsule désactivée des deux côtés par erreur de mon script, corrigé depuis) :
| Version | Images ratées | 99e |
|---|---|---|
| A phase 2 | 64 · 27 | 32 · 20 ms |
| B phase 3 avec rangée « Reprendre » affichée | 79 · 26 | 32 · 23 ms |
Rien de mesurable : pas plus lent, même avec la rangée. Mémoire du launcher ~47 Mo. La scène et le mode Art sont
des fenêtres à part : fermées, elles n'existent pas. Boucle d'inactivité de la scène : aucune tant qu'elle est désactivée.

### Réserves
- **Autorisation ADB** : la TV redemande « Autoriser le débogage USB ? » pour la clé de TvHome (clé non reconnue :
  l'autorisation a été oubliée). Tant que ce n'est pas validé (« Toujours autoriser »), la réactivation automatique du service,
  Ne pas déranger et le minuteur de veille ne peuvent pas passer par l'ADB local.
- Android retire le service d'accessibilité à chaque réinstallation : il est ré-activé depuis le PC pendant les essais.
- Le centre de contrôle s'ouvre par un retour à l'accueil avec le panneau (pas en surimpression sur la vidéo), puis ramène
  à l'appli d'origine à la fermeture.
- « Reprendre la lecture » relance l'appli (pas de reprise exacte à la minute : Android ne le permet pas sans accord de l'appli).
- Mode Art et scène choisissent nuit.jpg ou jour.jpg selon le lever / coucher du soleil de la météo.

### Mise à jour 3a (06/11, 16h25) — Retour long désactivé par défaut
Alban signale que « Retour semble désactivé » avec le service actif. Service retiré des services d'accessibilité de la TV,
option repassée à **désactivée par défaut** (`back_hold` = false : le service, même activé, ignore les touches ; pas de
réactivation automatique). À réactiver volontairement dans Réglages → Système seulement si Alban veut retenter.
Cause non établie : `adb input` ne passe pas par le filtre d'accessibilité, donc non reproductible depuis le PC.
Piste : le firmware Vestel gère mal le filtre de touches (le profil CleanYourTV cite un cas voisin chez un autre constructeur).

### Mise à jour 3a (06/11, 16h30) — Retour long VALIDÉ par Alban à la télécommande
Cause du premier échec : le filtre d'accessibilité ne transmet que l'appui et le relâchement (jamais les répétitions de l'appui
long). `ButtonService` utilise maintenant un minuteur de 700 ms. Résultat : Retour court = retour normal, Retour long = centre de
contrôle ; à la fermeture on revient à l'appli d'origine. Option de nouveau **activée par défaut** (Réglages → Système).
Réserve restante : Android retire le service à chaque réinstallation ; la réactivation automatique demande que Alban autorise
la clé de TvHome dans la fenêtre « Autoriser le débogage USB ? » (cocher « Toujours autoriser »).

## Phase 4 — Bench final et bilan (06/11/2026)

### Automatismes de lumières : supprimés (demande d'Alban)
Sauvegarde : `backup\2026-11-06-avant-phase4`. Plus de mode ciné, de lumière à la pause, de retour à la normale, ni d'extinction
après 30 min d'économiseur ; plus rien ne bouge au lancement d'une appli, d'une source HDMI ou d'une lecture. Gardé : le panneau
manuel dans le centre de contrôle (pièces, luminosité, scènes, pastille « Musique » par appui long sur une scène).
`MediaListener` ne sert plus qu'à « Reprendre la lecture ». Plus aucun résidu dans le code (vérifié).

### Bench final : 3 tours entrelacés (TvHome référence, TvHome finale, Google, tvOS Launcher), même TV, 16h44 à 17h50
La TV étant très bruitée cet après-midi (une même version fait de 27 à 83 images ratées), les mesures sont entrelacées et
comparées par médianes. Preuve de configuration : `bench\preuve-ab.txt` (anim=reduced backdrop=1, glass=true pour la finale).

| Version (médiane de 3 benchs) | Images ratées | % ratées | 99e | Images dessinées | Démarrage à froid | Mémoire launcher |
|---|---|---|---|---|---|---|
| TvHome référence (fin de phase 1) | 66 (54 · 67 · 66) | 3,2 % | 32 ms | ~2 050 | 0,48–0,52 s | 46 Mo |
| **TvHome finale** | **55 (55 · 29 · 64)** | **3,1 %** | **32 ms** | ~1 770 | 0,56 s | 54 Mo |
| Launcher Google | 85 (90 · 79 · 85) | 10,1 % | **125 ms** | ~840 | 1,4 s | 151 Mo |
| tvOS Launcher (source compilée) | 63 (63 · 44 · 70) | 11,2 % | 77–81 ms | ~550 | 1,27 s | 119 Mo |
| Plume (25/10, non re-mesuré : APK perdu) | 12 (11 · 12 · 20) | 2,3 % | 29 ms | ~530 | 0,39 s | 42 Mo |

Lecture honnête :
- **TvHome finale ≈ référence** (55 contre 66 en médiane, 99e identique) : rien de perdu mesurable malgré les phases 2, 3 et 4.
  Légères hausses : démarrage à froid 0,49 → 0,56 s, mémoire 46 → 54 Mo (menus, centre de contrôle, rangée « Reprendre », capsule).
- **Contre Google et tvOS Launcher**, TvHome a 3 fois moins d'images ratées en proportion et un 99e 2,5 à 4 fois meilleur (32 contre 77–125 ms).
- **Le matin (11h), la même base faisait 1,0 % d'images ratées** : la TV se dégrade dans la journée, les chiffres du matin et de
  l'après-midi ne sont pas comparables. Les % ci-dessus ne valent qu'entre eux, à cette période.
- Plume reste le plus léger en proportion (2,3 % ; il dessine 3 fois moins d'images) mais est daté du 25/10.

### Bilan : gardé / option / annulé
| Élément | Statut |
|---|---|
| Phase 1 : fond de fenêtre supprimé | gardé |
| Phase 1 : sons de navigation système | gardé, option désactivée par défaut |
| Phase 1 : assombrissement cuit dans l'image | annulé (pire) |
| Phase 1 : bande de verre dépoli en direct | annulée (3 % d'images ratées), remplacée par la capsule fabriquée |
| Phase 2 : grille 6 colonnes tvOS, zoom 1,13 | gardé (zoom 1,2 de tvOS non retenu) |
| Phase 2 : capsule de verre dépoli, nom d'appli supprimé, ombre sans coupure | gardé, capsule activée par défaut (réglable) |
| Phase 2 : réglages façon tvOS, menu d'appli, Autres applis, applis masquées, centre de contrôle | gardé |
| Phase 2 : photos jour / nuit (désert / New York) | gardé |
| Phase 3 : appui long sur Retour = centre de contrôle | gardé, validé par Alban, activé par défaut |
| Phase 3 : rangée « Reprendre la lecture », applis récentes, libérer la mémoire | gardé |
| Phase 3 : économiseur StandBy, couleur signature par appli, mode Art | gardé |
| Phase 3 : scène « Night Scenes » | gardé, **option désactivée par défaut** |
| Phase 4 : automatismes de lumières | supprimés |
| Code de rendu de tvOS Launcher | non repris (12 % d'images ratées, 99e 77–81 ms) : seules ses idées de menus et de mesures sont adaptées (voir NOTICE) |

### Décisions d'Alban
Phase 0 : touche Input OK sans réactiver `fusion.android.tv.demo`. Phase 2 : validée. Retour long : validé à la télécommande
(16h30). Publication GitHub : à faire après la fin de la mission. Photos : une de nuit (New York), une de jour (désert).
Lumières : tous les automatismes supprimés.

### Reste à faire / à noter
- **Publier sur GitHub** (dépôt préparé : README, LICENSE, NOTICE, docs, `verifier-publication.ps1`) : installer Git, créer le dépôt.
- **Autorisation ADB** : valider « Toujours autoriser » pour la clé de TvHome, sinon la réactivation automatique du service Retour long
  après une mise à jour et Ne pas déranger ne fonctionnent pas par l'ADB local.
- Android retire le service Retour long à chaque réinstallation : à ré-activer (Réglages → Système) si besoin.
- Retrouver l'APK de Plume pour le re-mesurer à la même période.
- Wanbo T2 Ultra (Amlogic T950D4, Mali-G31 MP2, 1 Go) : probablement plus fluide côté GPU, attention à la mémoire et au service
  d'accessibilité du constructeur.
