# Rapport de la nuit du 21 au 22/10/2026 : fluidité de TvHome

## En bref
- **Objectif battu, avec le Top Shelf actif.** Meilleur bench : **24,5 % d'images saccadées / médian 8 ms / 99e 36 ms**
  (meilleur historique : 46 % / 13 / 42 ; avec Top Shelf hier soir : 72 % / 26 / 53).
- Images réellement ratées (« Frame deadline missed ») : **393 → 81-156**. « Slow UI thread » : **228 → 62-74**.
- 4 essais sur 10 : 3 gardés, 1 annulé. Arrêt parce que l'objectif est battu.
- Aucune fonction ni aucun rendu changé. Installée sur la TV : version de 00h00 (APK du 22/10 00:00:09),
  `TOP_SHELF_ENABLED = true`, accueil vérifié au premier plan, aucun plantage dans le journal.

## Ce que les traces Perfetto ont montré
Traces dans `bench\traces\` (à ouvrir sur https://ui.perfetto.dev si besoin). Scripts réutilisables dans
`bench\outils\` (capture, analyse SQL, essai complet), décrits dans CLAUDE.md.

1. **Le goulot, c'est le GPU, pas le code Kotlin.** La TV a un **Mali-470** (OpenGL ES 2.0, très faible) et TvHome
   est rendu en **1920×1080** (et pas en 1280×720 comme on le pensait). Le thread principal passait ~11 ms par image
   à attendre le RenderThread, qui lui-même attendait un tampon libre (`dequeueBuffer`, `queueBuffer`), donc le GPU.
   Le CPU n'était pas saturé (RenderThread presque jamais en attente de processeur).
2. **Le coupable n°1 : les fondus créaient des calques hors écran plein écran.** `Crossfade` (fond + Top Shelf),
   l'image floutée et les noms d'applis mettaient leur transparence sur un calque : HWUI faisait un
   « alpha caused saveLayer **1920x1080** » (437 images sur 752) et « **1920x626** » (le Top Shelf, 282 images).
   C'est pour ça que le Top Shelf coûtait si cher : chaque changement de titre lançait deux fondus de 600 ms.
3. **Sans Top Shelf**, les 59 % restants venaient du même GPU : petits calques hors écran sur les libellés,
   et remplissages plein écran (photo + dégradé) pendant les défilements et le flou.
4. **Ce GPU déteste les calculs par pixel compliqués** (vu à l'essai 2) : un shader plus complexe coûte plus
   qu'un remplissage de plus. Il faut donc des dessins simples : couleur unie > dégradé 2 points > dégradé 3 points.
5. Après les correctifs, la grille est à 3-4 ms par image. Le reste du coût est sur la première rangée,
   pendant les fondus de 600 ms du Top Shelf (deux photos plein écran en même temps).

## Tous les essais
Chaque essai : `deploy.ps1` (APK neuf vérifié), TV réveillée, `bench.ps1 -ApresSeulement`, résultat dans `bench\`.

| # | Piste | Saccades | Médian | 99e | Deadline missed | Slow UI | Décision |
|---|---|---|---|---|---|---|---|
| réf | Avec Top Shelf, avant la nuit (`...2317-reference-topshelf`) | 74,8 % | 27 | 57 | 393 | 228 | référence |
| 1 | Fondus sans calque hors écran (`Fade.kt`, `ModulateAlpha`) | 48,4 % / 44,5 % | 14 / 10 | 53 / 53 | 358 / 340 | 189 / 176 | **gardé** |
| 2 | Dégradé du fond fusionné dans la photo (shader composé) | 67,0 % | 23 | 69 | 539 | 258 | annulé (pire) |
| 3 | Dégradé du Top Shelf limité aux 60 % de gauche | 35,1 % / 65,2 % | 8 / 22 | 42 / 44 | 207 / 285 | 125 / 157 | **gardé** |
| 4 | Dégradé du fond : 40 % du haut en couleur unie + dégradé 2 points | 70,6 % / **24,5 %** | 24 / **8** | 38 / **36** | 156 / **81** | 74 / **62** | **gardé** |

(Deux chiffres = 1er bench / bench de confirmation, même APK.)

### Attention à la mesure : le « % de saccades » est trompeur
Le même APK peut donner 35 % ou 65 % (essai 3), 70 % ou 24 % (essai 4). Ce chiffre de gfxinfo compte toutes les
images dont la *latence* dépasse 16,6 ms, y compris celles qui attendent simplement un tampon libre sans qu'aucune
image ne soit perdue (le compteur « High input latency » passe du simple au double entre deux benchs). Le médian
suit le même chemin. **Les indicateurs fiables sont « Frame deadline missed » (vraies images ratées) et le 99e
percentile** : ils ont baissé à chaque essai gardé, sur les deux benchs. J'ai décidé de garder ou d'annuler en
regardant surtout ces deux-là.

Autre biais du banc : chaque `input keyevent` lance une machine Java sur la TV (`app_process`, ~12 s de CPU sur
28 s de navigation). Ça pèse pareil sur toutes les mesures (Google compris), mais ça ajoute du bruit.

## Les modifications gardées (code)
- `Fade.kt` (nouveau) : copie simplifiée de `Crossfade`, même durée et même courbe, mais transparence appliquée
  dessin par dessin (`CompositingStrategy.ModulateAlpha`) au lieu d'un calque hors écran.
- `HomeScreen.kt` :
  - fond et Top Shelf : `Crossfade` → `Fade` ;
  - image floutée et libellé des tuiles : `compositingStrategy = ModulateAlpha` ;
  - dégradé noir du Top Shelf : peint sur 60 % de la largeur (au-delà il était transparent) ;
  - assombrissement du fond : rectangle uni sur les 40 % du haut + dégradé 2 points en dessous (au lieu d'un
    dégradé 3 points plein écran ; rectangle uni sauté quand il est transparent, en journée sur la 1re rangée).
- `Config.kt` : `TOP_SHELF_ENABLED = true`.

Seule nuance visuelle, invisible en pratique : pendant un fondu (600 ms, ou 180 ms pour un libellé), un texte et son
ombre s'estompent chacun de leur côté au lieu de s'estomper en bloc. L'ombre peut donc transparaître un instant sous
la lettre à mi-fondu. Les images fixes sont identiques au pixel près.

## Sauvegardes
- `backup\2026-10-21-avant-nuit\` : état avant la nuit (attention : `TOP_SHELF_ENABLED = false` dans cette copie,
  c'était l'état du moment). La copie de `gradle.properties` avait été vidée par la coupure de courant ; refaite.
- `backup\2026-10-21-essai1-garde\`, `backup\2026-10-21-essai3-garde\` : étapes intermédiaires.
- `backup\2026-10-22-final-nuit\` : la version installée.

## Pourquoi je ne recommande pas (encore) la réécriture en Canvas
Le coût n'est plus dans Compose : la grille tourne à 3-4 ms par image, et ce qui reste est du **remplissage GPU**
(photos plein écran en 1080p pendant les fondus). Un Canvas unique peindrait les mêmes pixels et ne gagnerait
presque rien. La réécriture ne vaut le coup que si on change *ce qui est peint*, ce qui touche au visuel
(voir décisions ci-dessous).

## Les décisions qui t'attendent
1. **Rendre l'accueil en 1280×720 au lieu de 1920×1080** : 2,25 fois moins de pixels à peindre pour le GPU, c'est
   de loin le plus gros levier restant. Mais l'image serait un peu moins nette (textes, logos). Pas fait : ça change
   le rendu.
2. **Fondus du Top Shelf plus courts** (600 ms → 300-400 ms) : moins d'images coûteuses. Changement visuel, pas fait.
3. **Le % de saccades n'est pas fiable** (voir plus haut) : veux-tu qu'on ajoute « deadline missed » comme
   indicateur principal dans CLAUDE.md et qu'on fasse 2-3 benchs par mesure ? Je l'ai déjà noté dans CLAUDE.md,
   à toi de valider.
4. Doublon dans `bench\` : `apres-2026-10-21-correctif-recompositions.txt` est identique à
   `apres-2026-10-21-recompositions.txt`. Supprimer l'un des deux ?
5. Pistes non tentées parce que l'objectif était battu : mise à jour de la BOM Compose (n'aiderait guère, le CPU
   n'est pas le goulot), baseline profile (inutile : l'appli est déjà précompilée en `speed`), R8 (déjà actif).
6. Petits fichiers laissés sur la TV (inoffensifs) : `/sdcard/tvhome_row0.png` (capture d'écran),
   `/data/local/tmp/tvhome_perfetto.txt`, `/data/misc/perfetto-traces/tvhome.pftrace`. Je n'ai rien supprimé
   hors de `bench\` et `backup\`.

## Déroulé
- 22h59 : reprise après la coupure de courant. État vérifié : sauvegarde intacte sauf `gradle.properties` (refait),
  APK de 22h46 installé, accueil OK.
- 23h03-23h10 : traces avec et sans Top Shelf. 23h17 : bench de référence.
- 23h18-00h12 : essais 1 à 4 (8 benchs). 00h13 : vérifications finales (premier plan, journal de plantages,
  captures d'écran de la rangée du haut et de la grille).
