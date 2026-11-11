# Mission « meilleur que Google » — rapport (25/10/2026)

TV Edenwood (Mali-470, 1920×1080), IP 192.168.1.24. Chaque ligne = 2 ou 3 benchs complets (`bench.ps1`,
TV redémarrée, 60 appuis de navigation). Mode actif prouvé à chaque bench par la ligne logcat
`TvHome anim=… backdrop=…`.

**Indicateur** : images ratées (« Frame deadline missed ») + 99e percentile.
**% d'images ratées** = ratées ÷ images dessinées : Plume dessine ~5× moins d'images que nous, la valeur
absolue seule est trompeuse.
**Budget (règle d'or)** : ratées dans la plage de référence (75–136) et 99e ≤ 38 ms.

## Tableau final

| Version | Images ratées | % d'images ratées | 99e (ms) | Images dessinées | Démarrage à froid |
|---|---|---|---|---|---|
| Launcher Google | 28 / 74 / 74 | 2,0 / 5,1 / 5,1 % | 42 / 46 / 46 | ~1 430 | 1,51–1,56 s |
| Plume | 11 / 12 / 20 | 2,1 / 2,3 / 3,7 % | 29 / 29 / 29 | ~530 | 0,40–0,42 s |
| TvHome avant mission (référence) | 75 / 100 / 136 | 2,9 / 3,8 / 5,2 % | 36 / 38 / 38 | ~2 600 | 0,50–0,52 s |
| Fond 0 Top Shelf dynamique — normal | 102 / 103 / 131 / 66 | 3,7 / 3,7 / 4,7 / 2,4 % | 36 / 36 / 36 / 34 | ~2 770 | 0,49–0,53 s |
| Fond 0 — réduit | 79 / 77 | 3,9 / 3,8 % | 32 / 32 | ~2 040 | 0,51–0,53 s |
| Fond 0 + prepareToDraw + préchargement voisines — normal | 108 / 96 | 3,9 / 3,5 % | 36 / 34 | ~2 790 | 0,51 s |
| Fond 0 + prepareToDraw + préchargement voisines — réduit | 75 / 71 | 3,7 / 3,5 % | 32 / 32 | ~2 045 | 0,50–0,52 s |
| Fond 0 + prepareToDraw + images 960 px — normal | 116 / 115 | 4,2 / 4,1 % | 34 / 34 | ~2 790 | 0,50–0,52 s |
| **Fond 1 Photo fixe — normal** | **21 / 56** | **0,8 / 2,0 %** | **24 / 32** | ~2 810 | 0,51 s |
| Fond 2 Fond uni sombre — normal | 31 / 43 | 1,1 / 1,5 % | 32 / 32 | ~2 840 | 0,50–0,52 s |
| **✅ Config par défaut installée : fond 1 + réduit** (sans prepareToDraw) | **19 / 19** | **1,0 / 0,9 %** | **20 / 30** | ~2 000 | 0,51 s |

La config par défaut est la plus stable et la meilleure de la journée : 19 images ratées aux 2 benchs,
soit 4× moins que Google et autant que Plume en proportion, avec un 99e de 20–30 ms (Plume : 29 ms).
Pas mesuré : fond 2 en animations réduites (peu d'intérêt, le fond 1 fait aussi bien).

Essais de la matinée (Top Shelf dynamique, animations normales) :

| Essai | Images ratées | % | 99e | Verdict |
|---|---|---|---|---|
| 1a fondu « par-dessus » | 75 / 142 / 92 | 2,9 / 5,5 / 3,5 % | 36 / 38 / 36 | annulé (pas de gain) |
| 1b pas de fondu en navigation rapide (attente 700 ms) | 125 / 75 | 4,5 / 2,7 % | 36 / 36 | gardé (moins de fondus inutiles, coût nul) |

## Ce que disent les chiffres
- **Le coût vient du Top Shelf dynamique** : le changement de photo plein écran à chaque appli survolée.
  Dès que le fond est figé (fond 1 ou 2), les images ratées passent de ~100 à 21–56 et le 99e de 36 à 24–32 ms.
- **Les fonds 1 et 2 battent Google et égalent ou battent Plume en proportion** (0,8–2,0 % contre 2,1–3,7 %
  pour Plume). Plume garde moins d'images ratées en valeur absolue, car il dessine 5× moins d'images.
- **La photo fixe ne coûte presque rien de plus que le fond uni.**
- **L'hypothèse « envoi des textures au GPU » est écartée** : la trace Perfetto ne la confirmait pas, et le
  correctif `prepareToDraw()` + préchargement ne change rien de net (108 / 96 contre ~100). Les images en
  960 px ne font pas mieux non plus (116 / 115).
- Les animations réduites font baisser le 99e de 36 à 32 ms en fond 0.

## Décisions appliquées (règle d'Alban : fond 0 corrigé > 2,5 % → fond 1 par défaut)
- **Par défaut : fond 1 (photo fixe, rotation toutes les 10 min) + animations réduites.** Ce sont les valeurs
  par défaut du code (`UiSettings.kt`) et les réglages de la TV, vérifiés dans le log.
- Le fond 0 (Top Shelf dynamique) et le fond 2 (fond uni) restent au choix dans
  Options → « Fond d'accueil ». Les animations normales restent au choix dans Options → « Animations ».

## Gardé / optionnel / annulé
| Élément | Statut |
|---|---|
| 1a fondu « par-dessus » | annulé |
| 1b pas de fondu en navigation rapide | gardé |
| 1c réglage Animations normales / réduites | gardé, **réduites par défaut** |
| Réglage « Fond d'accueil » (0 / 1 / 2) | gardé, **photo fixe par défaut** |
| prepareToDraw + préchargement des voisines | annulé (neutre, retiré sur décision d'Alban le 25/10) |
| Images du Top Shelf en 960 px | annulé (116 / 115, aucun gain) |

## Décisions d'Alban (25/10 soir)
1. Fond 1 + animations réduites validé visuellement.
2. prepareToDraw + préchargement retirés.
3. Config par défaut confirmée par 2 benchs (19 / 19 images ratées).

## Étape 2 — faux verre dépoli (25/10, 23h30–minuit)
Principe : `drawFrosted()` (`Wallpapers.kt`) peint la photo floutée précalculée, cadrée comme le fond plein
écran mais seulement sur la zone de l'élément, puis une teinte unie par-dessus. C'est opaque, sans flou temps réel ni calque.
En fond uni : pas de photo, fond opaque d'origine. Captures : `captures\verre-accueil.png`, `captures\verre-lumieres.png`.

| Config (fond 1 + réduit) | Images ratées | % | 99e |
|---|---|---|---|
| Sans verre | 19 / 19 | 1,0 / 0,9 % | 20 / 30 ms |
| Avec bande de verre sous la 1re rangée | 60 / 52 | 2,9 / 2,5 % | 34 / 34 ms |

- **Panneau Lumières en verre dépoli : gardé.** Coût nul sur l'accueil : le panneau n'existe que lorsqu'il est ouvert.
- **Bande de verre sous les applis : option désactivée par défaut** (Options → « Verre dépoli sous les applis »,
  ou `--ez glass_band true`). Elle reste dans le budget de départ, mais triple les images ratées.

## 👉 Où reprendre (arrêt le 26/10 à minuit)
**Prochaine étape : 3a Récents.**
- Facultatif : 2 benchs de contrôle de la config par défaut (bande désactivée), pour vérifier qu'on retrouve ~19 / 19.
- Ensuite : 3a Récents, 3b économiseur StandBy, 3bis (couleur dominante, son de navigation, mode Art),
  4 (centre de contrôle), bench final ×3, captures dans `bench\captures\`.

Rappels techniques :
- TV : 192.168.1.24:5555.
- Changer un réglage par ADB : `am start -n fr.alban.tvhome/.MainActivity --ei backdrop 0|1|2 --ez anim_reduced true|false --ez glass_band true|false`.
- Vérifier : `adb logcat -d -s TvHome:I`.
- `essai.ps1` avec déploiement peut rester bloqué à la fin (démon Gradle) : lancer le déploiement en tâche
  de fond, puis les benchs avec `-SansDeploy`.

Sauvegardes : `backup\2026-10-25-avant-premium`, `-apres-1b`, `-avant-prepare`, `-apres-prepare`.
