# Performance : règles et méthode

La puce est faible (Mali-470, OpenGL ES 2.0). Les applis sont rendues en 1920×1080 : **chaque remplissage plein
écran coûte environ 4 ms au GPU**. Le goulot est le GPU, pas le processeur : on compte les pixels peints par image.

## Règles de dessin
- Pas d'ombre portée : un liseré à la place.
- Pas de flou en direct (API 30) ni de `RenderEffect` : le flou est précalculé, une fois.
- Jamais de transparence animée sur un calque (`graphicsLayer { alpha }`, `Crossfade`) : cela crée un calque hors
  écran, soit un remplissage de plus. Utiliser `Fade` (fondu maison) et `CompositingStrategy.ModulateAlpha`.
- Ne peindre que là où c'est visible (un dégradé qui finit transparent ne couvre que sa zone utile).
- Dessins simples : couleur unie < dégradé à 2 points < dégradé à 3 points < shader composé.
- Lire les états animés dans `graphicsLayer` / `drawWithContent`, pas dans le corps des composables.
- Isoler les états qui changent souvent dans de petits composables.
- Les menus sont dans leur propre fenêtre : fermés, ils n'existent pas, coût nul sur l'accueil.

## Verre dépoli sans flou en direct
`bakeCapsule()` (`Wallpapers.kt`) fabrique, à chaque changement de photo et hors du thread principal, l'image de la
capsule de la première rangée : la photo à cet endroit réduite au quart, floutée légèrement, assombrie comme le fond,
voile clair, reflet, liseré, coins arrondis. À l'affichage, c'est une simple image dessinée sans filtrage.
Les menus (`Menus.kt`) utilisent la photo floutée précalculée (`drawFrosted`), opaque.

## Mesurer
- `bench.ps1 -ApresSeulement` : redémarre la TV, attend 90 s, mesure le démarrage à froid, puis 60 appuis de
  navigation, et relève `dumpsys gfxinfo`.
- Indicateur retenu : **« Frame deadline missed »** (images ratées) et **99e percentile**. Le pourcentage
  d'images saccadées et le médian sont bimodaux et ne servent pas à décider.
- **Le banc est bruité** : la même version peut faire de 17 à 83 images ratées selon le moment (chaleur de la TV,
  état de la mémoire). Pour comparer deux versions, **alterner** A, B, A, B sur la même période
  (`bench\outils\ab.ps1`) et comparer les médianes, jamais contre un chiffre ancien.
- Prouver la configuration mesurée : `adb logcat -d -s TvHome:I` (ligne `anim=… backdrop=… glass=…`).
- Sauvegarder `app\src` avant chaque essai, une seule variable à la fois.

## Essais gardés et annulés (résumé)
| Essai | Résultat |
|---|---|
| Fond de fenêtre supprimé | gardé |
| Photo fixe au lieu du fond dynamique par défaut | gardé (divise les images ratées par 4) |
| Dégradé d'assombrissement cuit dans les images | annulé (pire) |
| Bande de verre flouée en direct | annulée (3 % d'images ratées) |
| Capsule de verre fabriquée une fois | gardée |
| `prepareToDraw` + préchargement des images | annulé (neutre) |
