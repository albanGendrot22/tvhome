# Rapport Hue du 22/10/2026

> **Historique.** Les automatismes décrits ici (mode ciné, retour à la normale, pause, musique, extinction après
> l'économiseur) ont été **supprimés le 06/11/2026**. Il ne reste que la commande manuelle (pièces, scènes, « Musique »)
> dans le centre de contrôle. Voir `RAPPORT-DA.md`.

## En bref
- **Le pont est appairé et pilote le salon** (pont 192.168.1.45, API locale v2, certificat épinglé). Mode ciné, retour
  à la normale et pause **testés en vrai avec Netflix et validés par Alban** (lumières vues à l'œil).
- **Le panneau fermé ne coûte rien** : 146 / 83 images ratées « après » contre 169 / 74 « avant », 99e identique.
- **Un point ne peut pas être fait** : l'appui long sur Home ne peut pas ouvrir le panneau sur Android 11 (voir plus bas).
  L'icône ampoule à côté de la météo, elle, marche.
- Non testés faute de temps ou de matériel : Disney+ et Prime Video (pause), l'extinction après 30 min d'économiseur,
  et l'automatisme Apple Music (appli absente de la TV).

## Ce qui a été fait (fichiers)
- `Hue.kt` : le pont. Découverte (mDNS `_hue._tcp`, sinon `discovery.meethue.com`), appairage, lecture des pièces /
  scènes / lumières, pilotage. API locale uniquement, rien de cloud.
- `Lights.kt` : réglages, état du pont, les 5 automatismes, une file de commandes (jamais deux ordres en même temps).
- `LightsPanel.kt` : le panneau, l'appairage, l'icône ampoule.
- `MediaListener.kt` : lecture / pause des applis de ciné (MediaSession).
- Branchements dans `HomeScreen.kt` (icône, options « Lumières Hue… »), `MainActivity.kt`, `Safety.kt`,
  `AndroidManifest.xml`.

## Sécurité et règles respectées
- **Clé du pont** : uniquement dans les préférences privées de l'appli (`hue`), jamais dans le code ni dans un fichier
  du projet. Aucune clé n'est écrite dans les journaux.
- **Certificat du pont épinglé** : le pont a un certificat auto-signé. À l'appairage, on retient son empreinte
  (SHA-256) et on refuse ensuite tout autre certificat, donc un autre appareil du réseau ne peut pas se faire passer
  pour le pont. Le nom d'hôte n'est volontairement pas vérifié (le certificat porte l'identifiant du pont, pas son IP).
- **On lit et on pilote, rien d'autre** : aucune scène ni aucun réglage créé ou modifié dans le pont. Les scènes sont
  seulement *rappelées* (`recall`). Le mode ciné règle les lumières directement, sans toucher aux scènes.
- Si le pont ne répond pas : l'icône ampoule passe grisée, rien d'autre ne change. Les appels réseau ont un
  délai maximum de 3 s et tournent hors du thread principal.

## Panneau « Lumières »
- Ouvert par l'icône ampoule à côté de la météo (appui sur OK).
- Tiers droit de l'écran, fond **opaque** `#16161A`, sans flou ni fondu : seul un glissement de position
  (`translationX`, 260 ms) depuis la droite. Il vit dans sa propre fenêtre : tant qu'il est fermé, il n'existe pas.
- Pièces avec interrupteur (OK) et luminosité (gauche / droite, pas de 10 %, une seule commande envoyée au pont
  après 350 ms sans appui, car il n'accepte qu'environ un ordre par seconde par pièce), scènes Hue existantes en
  pastilles, raccourcis « Pause lumière » et « Musique ».
- **État lu une fois à l'ouverture** (et 1,5 s après le rappel d'une scène). Aucune interrogation en boucle.
- **Flux d'événements du pont : non fait.** Les automatismes n'en ont pas besoin, et il aurait gardé une connexion
  ouverte en permanence : pas d'intérêt à le payer sur cette TV.

## Les automatismes (tous activés, chacun avec son interrupteur : Options → Lumières Hue…)
| | Automatisme | État |
|---|---|---|
| a | Mode ciné : Netflix, Disney+, Prime Video, Canal+, ou source HDMI Apple TV → salon à 20 % en blanc chaud | **testé avec Netflix, validé** ; Disney+, Prime Video et Apple TV HDMI branchés de la même façon, non testés ; Canal+ n'est pas installée |
| b | Retour sur l'accueil → état d'avant restauré (couleur, blanc, luminosité de chaque lampe) | **testé, validé** |
| c | Pause à 50 %, reprise au mode ciné | **Netflix : testé, validé** ; autres applis : voir tableau |
| d | Musique | pastille « Musique » dans le panneau (Apple Music absent de la TV) ; **il faut choisir la scène** |
| e | Extinction du salon après 30 min d'économiseur | **non testé** (30 min d'attente) ; voir limites |

### Pause, appli par appli
| Appli | Publie lecture / pause ? |
|---|---|
| Netflix | **Oui.** 6 pauses et 6 reprises détectées en 3 min, lumières suivies |
| Disney+ | non testé |
| Prime Video | non testé |
| Canal+ | non installée |
| Apple TV en HDMI | impossible par nature (la TV ne voit pas la lecture d'un boîtier externe) : bouton « Pause lumière » du panneau |

Détails techniques : Android ne donne l'accès aux sessions média qu'à un « écouteur de notifications ». `MediaListener`
est déclaré dans ce seul but et ne lit aucune notification. Il a été autorisé pour TvHome par la commande
`cmd notification allow_listener` (celle du bouton « Autoriser la détection lecture / pause » des options).
Réversible : `adb shell cmd notification disallow_listener fr.alban.tvhome/fr.alban.tvhome.MediaListener`.

Deux garde-fous ajoutés : l'état « avant ciné » est ignoré s'il a plus de 6 h (TV éteinte en plein film : on ne
rallume pas l'ambiance de la veille), et les états de chargement / mise en mémoire tampon ne font rien.

## Bench (2 minimum, images ratées + 99e)
Même jour, même heure (20h30-21h), mêmes conditions (voile de nuit actif), même APK pour les deux séries
de benchs sauf le code Hue.

| | Fichier `bench\apres-2026-10-22-...` | Images ratées | 99e | Slow UI |
|---|---|---|---|---|
| Avant Hue | `2029-hue-avant` | 169 | 38 ms | 73 |
| Avant Hue | `2035-hue-avant-2` | 74 | 36 ms | 58 |
| **Après Hue** | `2050-hue-apres` | 146 | 38 ms | 70 |
| **Après Hue** | `2056-hue-apres-2` | 83 | 36 ms | 65 |

Les deux séries sont indiscernables (le 1er bench de chaque série est le « mauvais » du bimodal habituel).
**Panneau fermé = coût nul mesurable.** Le démarrage à froid et la mémoire n'ont pas non plus bougé (voir les fichiers).
Le coût du panneau *ouvert* n'a pas été mesuré (il n'y a rien à mesurer en navigation normale).

## Ce qui ne marche pas comme demandé
1. **Appui long sur Home** : sur cette TV, l'appui long sur Home est intercepté par le système, qui ouvre « toutes les
   applis » des réglages (`START act=android.intent.action.ALL_APPS pkg=com.android.tv.settings`, journal
   d'Android). Un écran d'accueil ne reçoit jamais cet appui. Le panneau s'ouvre donc seulement par l'icône ampoule.
   Un moyen détourné serait de redéfinir une autre touche, mais ça demande un service d'accessibilité : pas fait.
2. **Extinction après 30 min d'économiseur** : elle repose sur la notification « économiseur démarré » du système et
   un minuteur dans le processus de TvHome. Si Android ferme ce processus en arrière-plan pendant les 30 min
   (la TV n'a que 1,3 Go), le minuteur est perdu et les lumières ne s'éteignent pas. Une vraie alarme système
   (`AlarmManager`) est plus fiable ; à faire si le test de 30 min échoue.

## Décisions qui t'attendent
1. **Scène « Musique »** : aucune n'est choisie, donc la pastille « Musique » n'apparaît pas. Pour la choisir : ouvre
   le panneau, appui long sur la scène voulue (15 existantes : Atténué, Aurore boréale, Concentration, Détente,
   Fairfax, Foot, Lecture, Lumineux, Meriete, Soho, Stimulation, Veilleuse, Étreinte chaleureuse, Île d'émeraude…).
   Je n'ai pas choisi à ta place.
2. **Pièce du salon** : une seule pièce existe (« Salon », 2 lumières), je l'ai confirmée comme salon ; modifiable dans
   Options → Lumières Hue… → « Pièce du salon ».
3. **Voile de nuit** (ton idée de ce soir) : tu ne vois aucune différence jour / nuit. Je le teste comme prochaine
   expérience (interrupteur dans Config.kt, 2 benchs jour et 2 benchs nuit) ; si les images ratées baissent et que tu
   ne vois rien, on le coupe pour de bon.
4. **Appui long sur Home** : accepter l'icône ampoule seule, ou veux-tu qu'on étudie un raccourci par une autre
   touche de la télécommande (service d'accessibilité) ?

## Pour annuler
- Désactiver un automatisme : Options → Lumières Hue… → l'interrupteur.
- Tout retirer : restaurer `backup\2026-10-22-avant-hue\` puis `deploy.ps1` ; le pont garde la clé de TvHome
  (la supprimer dans l'appli Hue → réglages → accès / applications autorisées, si tu veux).
- Version de ce soir : `backup\2026-10-22-final-hue\`.
