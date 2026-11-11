# Rapport de la journée du 22/10/2026

## En bref
- **Indicateur officiel** (images ratées « Frame deadline missed » + 99e, 2 benchs minimum) : inscrit dans CLAUDE.md.
- **Doublon supprimé** : `bench\apres-2026-10-21-correctif-recompositions.txt` (vérifié identique avant suppression).
- **Fondus du Top Shelf** : ni 400 ms ni 300 ms ne font *vraiment* baisser les images ratées → **on reste à 600 ms**.
- **Rendu 1280×720 limité à TvHome** : **pas de méthode propre sur Android 11**, je n'ai pas forcé (explications
  plus bas). Donc pas de captures « après » et pas d'option dans Config.kt.
- **Installé sur la TV** : version du 22/10 à 10h04, fondus 600 ms, Top Shelf actif, rendu inchangé par rapport à
  cette nuit. Accueil au premier plan vérifié, aucun plantage ni ANR dans le journal.

## Les mesures (toutes de jour, voile de nuit inactif)
Les mesures de la nuit ne sont pas comparables : la nuit, le voile ajoute un dessin plein écran. J'ai donc refait
une référence de jour.

| Essai | Fichiers `bench\apres-2026-10-22-...` | Images ratées | 99e | Décision |
|---|---|---|---|---|
| Référence, fondus 600 ms | `0905`, `0911`, `1010` | 62 / 117 / 67 (moy. 82) | 34 / 36 / 34 | **gardé** |
| A : fondus 400 ms | `0938`, `0944` | 95 / 115 (moy. 105) | 34 / 36 | annulé |
| B : fondus 300 ms | `0951`, `0957`, `1003` | 57 / 49 / 85 (moy. 64) | 34 / 34 / 34 | annulé |

**Pourquoi 600 ms :** 300 ms a une moyenne un peu plus basse, mais les plages se recouvrent largement
(49-85 contre 62-117) et le 99e est identique. Comme 300 ms rend aussi ~10 % d'images en moins (fondus plus
courts), l'écart rapporté au nombre d'images est encore plus faible (2,8 % contre 3,2 %). Ce n'est pas une baisse
nette. 400 ms n'a rien gagné du tout. Selon ta règle (« la durée la plus longue qui fait vraiment baisser »),
aucune ne la fait vraiment baisser, donc j'ai gardé 600 ms.

La durée est maintenant un réglage, `Config.SHELF_FADE_MS = 600` (photo de fond + texte du Top Shelf ; le même
fondu sert aussi au changement de fond d'écran toutes les 10 min). Tu peux la changer facilement si tu préfères
l'effet plus vif.

## Rendu en 1280×720 : pourquoi je n'ai rien installé
Tu voulais une méthode limitée à TvHome, sans `wm size`. J'ai vérifié dans le code source d'Android 11
(`WindowState.java`, `WindowStateAnimator.java`, `WindowManagerService.java`, `ViewRootImpl.java`) :

- La seule méthode publique est le drapeau de fenêtre `FLAG_SCALED`. Avec ce drapeau, le tampon de dessin prend la
  taille *mesurée* par la fenêtre, et Android l'agrandit jusqu'à la taille déclarée de la fenêtre.
- Mais Android mesure toujours le contenu exactement à la taille déclarée (`getRootMeasureSpec` → EXACTLY). Résultats :
  - fenêtre déclarée en 1920×1080 → tampon 1920×1080, agrandissement ×1 (aucun gain) ;
  - fenêtre déclarée en 1280×720 → tampon 1280×720, mais affiché en petit dans un coin, sans agrandissement ;
  - fenêtre en « plein écran » (`MATCH_PARENT`) → le facteur calculé devient négatif (inutilisable).
- Pour obtenir « fenêtre 1920×1080 + tampon 1280×720 », il faudrait trafiquer la mesure interne d'Android
  (`DecorView` / `ViewRootImpl`) par réflexion. Ou bien faire dessiner l'accueil dans un écran virtuel 1280×720
  affiché dans une `SurfaceView`, ce qui ajoute un passage de composition GPU (une partie du gain perdue) et oblige
  à rerouter à la main la télécommande et les menus. Les deux sont des bidouilles fragiles, je ne les ai pas faites.
- Sur Android 13 et plus, il existe une vraie réduction de résolution par appli (mode jeu). Pas sur ta TV (Android 11).

Les captures « avant » sont quand même dans `bench\captures\` (`avant-1080p-topshelf.png`, `avant-1080p-grille.png`)
et l'outil `bench\outils\captures.ps1` est prêt si on retrouve une piste.

## Incidents de la journée
- **Script d'essai bloqué 20 min** (essai A). Le démon Gradle s'était arrêté pendant la nuit : `deploy.ps1` l'a
  relancé, et le démon a gardé ouvert le tuyau de sortie que le script attendait. L'installation avait bien eu lieu.
  Corrigé dans `bench\outils\essai.ps1` : sorties dans des fichiers (`deploy-dernier.log`, `bench-dernier.log`), et
  arrêt si `apres.txt` n'a pas été mis à jour.
- **Config.kt abîmé une minute** : une commande PowerShell a relu le fichier dans le mauvais encodage (accents
  cassés). Je l'ai remis depuis la sauvegarde prise juste avant, puis modifié avec l'éditeur. Rien n'a été compilé
  dans cet état.

## Sauvegardes
`backup\2026-10-22-avant-jour`, `-avant-essaiB`, `-avant-ref600-3`, `-final-jour` (= version installée).

## Ce que tu dois décider ce soir
1. **Durée des fondus** : 600 ms installé. Si tu trouves l'effet trop lent à l'usage, 300 ms ne coûte pas plus cher
   (ce n'est pas mesurablement plus fluide, mais pas pire non plus). Il suffit de changer `SHELF_FADE_MS`.
2. **1280×720** : abandonner, ou accepter une bidouille (réflexion ou écran virtuel) en sachant qu'elle peut casser
   à une mise à jour et compliquer la télécommande ? Mon avis : abandonner, la fluidité actuelle est déjà meilleure
   que le launcher Google.
3. **Économiseur d'écran** : la TV utilise celui de Google (`com.google.android.backdrop`), pas les photos de TvHome.
   D'après CLAUDE.md, ça arrive après un déclenchement du filet de sécurité. Je ne sais pas depuis quand, et je n'ai
   pas le droit de toucher aux réglages de la TV. Pour remettre les photos : appui long sur une appli → Options de
   l'accueil → « Utiliser l'économiseur d'écran photos ». Ça vaut aussi le coup de vérifier que le filet de sécurité
   n'a pas été déclenché (le launcher Google est toujours désactivé, donc a priori non).
