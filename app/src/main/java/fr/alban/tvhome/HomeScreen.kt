@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)

package fr.alban.tvhome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TextShadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 14f)
private val SolidBackdrop = Color(0xFF0E0E12)
// Capsule de verre autour de la première rangée : aplat translucide et liseré
private val CapsuleFill = Color.White.copy(alpha = 0.13f)
private val CapsuleEdge = Color.White.copy(alpha = 0.28f)
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

/**
 * Sur une TV, Compose recentre la tuile sélectionnée à 30 % du haut de l'écran, ce qui écrase la Top Shelf.
 * Ici on ne défile que si la tuile sort de l'écran, et le reste est piloté à la main.
 */
private val GentleScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = containerSize * 0.1f
        val trailing = offset + size
        return when {
            offset < margin -> offset - margin
            trailing > containerSize - margin -> trailing - (containerSize - margin)
            else -> 0f
        }
    }
}

@Composable
fun HomeScreen(
    apps: List<TvApp>,
    wallpaper: Wallpaper?,
    weather: Weather?,
    homeSignal: Int,
    onLaunch: (TvApp) -> Unit,
    onInfo: (TvApp) -> Unit,
    onReorder: (List<TvApp>) -> Unit,
    onHide: (TvApp) -> Unit,
    onUnhideAll: () -> Unit,
    otherPkgs: Set<String> = emptySet(),
    hiddenApps: List<TvApp> = emptyList(),
    onUnhide: (TvApp) -> Unit = {},
    onRename: (TvApp, String?) -> Unit = { _, _ -> },
    onOther: (TvApp, Boolean) -> Unit = { _, _ -> },
    onResetLayout: () -> Unit = {},
    settingsSignal: Int = 0,
    onControlClosed: () -> Unit = {},
    resume: List<ResumeItem> = emptyList(),
    onResume: (ResumeItem) -> Unit = {},
    onResumeRemove: (ResumeItem) -> Unit = {},
    sceneSignal: Int = 0,
    sceneKind: Int = 0,
    sources: List<TvSource> = emptyList(),
    onSource: (TvSource) -> Unit = {},
    onHideSource: (TvSource) -> Unit = {},
    options: HomeOptionsActions? = null,
    picks: Map<String, List<Pick>> = emptyMap(),
    loadPick: suspend (Pick) -> Wallpaper? = { null },
    lightsSignal: Int = 0,
) {
    val config = LocalConfiguration.current
    val screenW = config.screenWidthDp.dp
    val screenH = config.screenHeightDp.dp
    val sidePad = Config.SIDE_PAD_DP.dp
    val gap = Config.GAP_DP.dp
    val tileW = (screenW - sidePad * 2 - gap * (Config.COLUMNS - 1)) / Config.COLUMNS
    val shelfH = screenH * 0.58f
    // Les sources suivent la même grille que les applis
    val srcCols = Config.COLUMNS
    val srcW = tileW

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var focusedRow by remember { mutableIntStateOf(0) }
    var moving by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<TvApp?>(null) }
    var srcMenuFor by remember { mutableStateOf<TvSource?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var showLights by remember { mutableStateOf(false) }
    var showFolder by remember { mutableStateOf(false) }
    var renameFor by remember { mutableStateOf<TvApp?>(null) }
    // Mode Art (1) : la photo seule ; scène (2) : la photo et une horloge géante. N'importe quelle touche ferme.
    var sceneMode by remember { mutableIntStateOf(0) }
    val sceneOn by UiSettings.scene
    val lastKey = remember { longArrayOf(android.os.SystemClock.uptimeMillis()) }
    LaunchedEffect(sceneSignal) { if (sceneSignal > 0) sceneMode = sceneKind }
    // Scène activée : elle se lance seule après 5 minutes sans touche. Désactivée : aucune boucle, rien ne tourne.
    LaunchedEffect(sceneOn) {
        if (!sceneOn) return@LaunchedEffect
        while (true) {
            delay(30_000)
            val idle = android.os.SystemClock.uptimeMillis() - lastKey[0] > 5 * 60_000L
            if (idle && sceneMode == 0 && menuFor == null && srcMenuFor == null && renameFor == null &&
                !showOptions && !showLights && !showFolder
            ) sceneMode = 2
        }
    }
    LaunchedEffect(lightsSignal) { if (lightsSignal > 0) showLights = true }
    LaunchedEffect(settingsSignal) { if (settingsSignal > 0 && options != null) showOptions = true }
    // Appli sélectionnée en première rangée : lue seulement par ShelfDriver, pas par tout l'écran
    val focusedPkg = remember { mutableStateOf<String?>(null) }
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requester(pkg: String) = requesters.getOrPut(pkg) { FocusRequester() }

    // Grille principale : les applis hors dossier, puis la tuile « Autres applis » s'il y en a de rangées
    val mainApps = apps.filter { it.pkg !in otherPkgs }
    val otherApps = apps.filter { it.pkg in otherPkgs }
    val gridApps = if (otherApps.isEmpty()) mainApps else mainApps + FolderApp
    val rows = gridApps.chunked(Config.COLUMNS)
    // « Reprendre la lecture » : une rangée glissée juste sous la première rangée d'applis, seulement s'il y a
    // quelque chose à reprendre. Elle compte comme une rangée de plus pour le défilement et le focus.
    val hasResume = resume.isNotEmpty()
    val resumeRows = if (hasResume) 1 else 0
    fun logicalRow(r: Int) = if (hasResume && r >= 1) r + 1 else r
    // Les animations ne sont lues qu'au moment du dessin : elles redessinent le fond sans recalculer l'écran
    val reduced by UiSettings.reducedAnimations
    val blurAlpha = animateFloatAsState(if (focusedRow > 0) 1f else 0f, if (reduced) snap() else tween(450), label = "blur")
    val nightAlpha = remember { mutableFloatStateOf(0f) }
    if (Config.NIGHT_VEIL_ENABLED) NightDriver(weather, nightAlpha)

    // Top Shelf façon Apple TV : l'appli sélectionnée en première rangée affiche ses titres tendance
    val pickImage = remember { mutableStateOf<Wallpaper?>(null) }
    val shownPick = remember { mutableStateOf<Pick?>(null) }
    val backdropMode by UiSettings.backdropMode
    ShelfDriver(focusedPkg, focusedRow == 0, picks, loadPick, pickImage, shownPick, loadImages = backdropMode == UiSettings.BACKDROP_SHELF)

    fun goTop() {
        moving = null
        scope.launch {
            listState.animateScrollToItem(0)
            delay(32)
            gridApps.firstOrNull()?.let { runCatching { requester(it.pkg).requestFocus() } }
        }
    }

    fun move(pkg: String, delta: Int) {
        val i = mainApps.indexOfFirst { it.pkg == pkg }
        val j = i + delta
        if (i < 0 || j !in mainApps.indices) return
        val list = mainApps.toMutableList()
        list.add(j, list.removeAt(i))
        onReorder(list + otherApps)
    }

    // Retour : sort du mode déplacement, sinon remonte en haut (comme le bouton Menu de l'Apple TV)
    BackHandler { if (moving != null) moving = null else goTop() }
    LaunchedEffect(homeSignal) { if (homeSignal > 0) goTop() }

    // Focus initial sur la première app
    LaunchedEffect(gridApps.isNotEmpty()) {
        if (gridApps.isNotEmpty()) {
            delay(100)
            runCatching { requester(gridApps.first().pkg).requestFocus() }
        }
    }

    // La Top Shelf s'efface dès qu'on descend, revient quand on remonte sur la première rangée
    LaunchedEffect(focusedRow) {
        when {
            focusedRow == 0 && (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) ->
                listState.animateScrollToItem(0)
            focusedRow == 1 && listState.firstVisibleItemIndex == 0 -> listState.animateScrollToItem(1)
        }
    }

    // En mode déplacement, le focus suit l'app qu'on bouge
    LaunchedEffect(apps, moving) {
        val pkg = moving ?: return@LaunchedEffect
        val idx = gridApps.indexOfFirst { it.pkg == pkg }
        if (idx < 0) return@LaunchedEffect
        val r = idx / Config.COLUMNS
        val item = r + 1 + (if (hasResume && r >= 1) 1 else 0)
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == item }) listState.scrollToItem(item)
        delay(16)
        runCatching { requester(pkg).requestFocus() }
    }

    val shownImage = { if (backdropMode == UiSettings.BACKDROP_SHELF) pickImage.value ?: wallpaper else wallpaper }
    // Verre dépoli : la version floutée du fond affiché (rien en fond uni)
    val glass = { if (backdropMode == UiSettings.BACKDROP_SOLID) null else shownImage()?.blurred }
    val appContext = LocalContext.current.applicationContext
    val metrics = LocalContext.current.resources.displayMetrics
    val screen = Size(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
    val glassBand by UiSettings.glassBand
    val capsule = remember { mutableStateOf<ImageBitmap?>(null) }

    Box(
        Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent {
            lastKey[0] = android.os.SystemClock.uptimeMillis() // pour la scène : dernière touche
            false
        }
    ) {
        if (glassBand) CapsuleBaker(shownImage, tileW, shelfH, sidePad, capsule)
        Backdrop(
            shownImage,
            { blurAlpha.value }, { nightAlpha.floatValue }, reduced,
            solid = backdropMode == UiSettings.BACKDROP_SOLID,
            scrim = { shownPick.value != null },
        )

        CompositionLocalProvider(LocalBringIntoViewSpec provides GentleScroll) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item(key = "shelf") { TopShelf(shelfH, weather, reduced, { showLights = true }) { shownPick.value } }
            rows.forEachIndexed { rowIndex, row ->
              item(key = "row$rowIndex") {
                Row(
                    Modifier.fillMaxWidth()
                        .then(
                            // Capsule de verre autour de la première rangée, façon tvOS : un aplat translucide arrondi
                            // et un liseré, sans flou ni photo (la bande en vrai verre dépoli coûtait 3 % d'images ratées)
                            if (rowIndex == 0 && glassBand) Modifier
                                .padding(start = sidePad - 16.dp, end = sidePad - 16.dp, top = 6.dp)
                                .drawBehind {
                                    val img = capsule.value
                                    if (img != null) {
                                        // Image déjà fabriquée à la bonne taille : dessinée telle quelle, sans filtrage
                                        drawImage(
                                            img,
                                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                            filterQuality = FilterQuality.None,
                                        )
                                    } else {
                                        val r = CornerRadius(30.dp.toPx())
                                        drawRoundRect(CapsuleFill, cornerRadius = r)
                                        drawRoundRect(CapsuleEdge, cornerRadius = r, style = Stroke(width = 1.dp.toPx()))
                                    }
                                }
                                // Même espace en haut et en bas de la capsule (le nom de l'appli n'est plus affiché)
                                .padding(16.dp)
                            else Modifier.padding(start = sidePad, end = sidePad, top = 22.dp)
                        ),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    row.forEach { app ->
                        key(app.pkg) {
                            AppTile(
                                app = app,
                                width = tileW,
                                isMoving = moving == app.pkg,
                                focusRequester = requester(app.pkg),
                                onFocused = {
                                    NavSound.onFocus(appContext, logicalRow(rowIndex), row.indexOf(app))
                                    focusedRow = logicalRow(rowIndex)
                                    if (rowIndex == 0) focusedPkg.value = app.pkg
                                },
                                onClick = { if (app.pkg == FOLDER_PKG) showFolder = true else onLaunch(app) },
                                onLongClick = { if (app.pkg != FOLDER_PKG) menuFor = app },
                                onMove = { delta -> move(app.pkg, delta) },
                                onStopMoving = { moving = null },
                            )
                        }
                    }
                }
              }
              if (rowIndex == 0 && hasResume) {
                item(key = "resume") {
                    ResumeRow(
                        items = resume, appFor = { pkg -> apps.firstOrNull { it.pkg == pkg } },
                        tileW = tileW, gap = gap, sidePad = sidePad,
                        requester = { requester("resume:$it") },
                        onFocused = { NavSound.onFocus(appContext, 1, 0); focusedRow = 1 },
                        onClick = onResume, onLongClick = onResumeRemove,
                    )
                }
              }
            }
            if (sources.isNotEmpty()) {
                item(key = "sources-title") {
                    Text(
                        "Sources",
                        modifier = Modifier.padding(start = sidePad + 4.dp, top = 40.dp, bottom = 2.dp),
                        style = TextStyle(
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.75f), shadow = TextShadow,
                        ),
                    )
                }
                itemsIndexed(sources.chunked(srcCols), key = { i, _ -> "src$i" }) { i, row ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = sidePad, end = sidePad, top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        row.forEach { src ->
                            key(src.hideKey) {
                                SourceTile(
                                    source = src,
                                    width = srcW,
                                    focusRequester = requester(src.hideKey),
                                    onFocused = {
                                        NavSound.onFocus(appContext, rows.size + resumeRows + i, row.indexOf(src))
                                        focusedRow = rows.size + resumeRows + i
                                    },
                                    onClick = { onSource(src) },
                                    onLongClick = { srcMenuFor = src },
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }

    srcMenuFor?.let { src ->
        AppMenuCard(
            glass, src.name,
            listOf("Masquer de l'accueil" to { srcMenuFor = null; onHideSource(src) }),
            onDismiss = { srcMenuFor = null },
        )
    }

    menuFor?.let { app ->
        val inOther = app.pkg in otherPkgs
        AppMenuCard(
            glass, app.label,
            buildList {
                add("Renommer" to { menuFor = null; renameFor = app })
                if (!inOther) add("Déplacer" to { menuFor = null; moving = app.pkg })
                add(
                    (if (inOther) "Remettre sur l'accueil" else "Ranger dans Autres applis") to
                        { menuFor = null; onOther(app, !inOther) }
                )
                add("Masquer de l'accueil" to { menuFor = null; onHide(app) })
                add("Infos sur l'app" to { menuFor = null; onInfo(app) })
                add("Réinitialiser la disposition" to { menuFor = null; onResetLayout() })
                if (options != null) add("Réglages de l'accueil…" to { menuFor = null; showOptions = true })
            },
            onDismiss = { menuFor = null },
        )
    }

    renameFor?.let { app ->
        RenameCard(
            glass, app.label,
            onDone = { renameFor = null; onRename(app, it) },
            onOriginal = { renameFor = null; onRename(app, null) },
            onDismiss = { renameFor = null },
        )
    }

    if (showFolder) {
        FolderScreen(
            glass, otherApps,
            onLaunch = onLaunch,
            onMenu = { menuFor = it },
            onDismiss = { showFolder = false },
        )
    }

    if (sceneMode != 0) {
        SceneDialog(wallpaper, weather, art = sceneMode == 1) {
            sceneMode = 0
            lastKey[0] = android.os.SystemClock.uptimeMillis()
        }
    }

    if (showOptions && options != null) {
        SettingsMenu(
            options, glass, hiddenApps,
            onLights = { showOptions = false; showLights = true },
            onUnhide = onUnhide,
            onUnhideAll = onUnhideAll,
            onResetLayout = onResetLayout,
            onDismiss = { showOptions = false },
        )
    }

    // Panneau Lumières : n'existe que lorsqu'il est ouvert (rien à dessiner ni à recalculer sinon)
    if (showLights) {
        ControlCenter(
            glass, sources, onSource, apps, onLaunch,
            onArt = { sceneMode = 1 }, onScene = { sceneMode = 2 },
            onSettings = { showOptions = true },
            onDismiss = { showLights = false; onControlClosed() },
        )
    }
}

/** Icône ampoule à côté de la météo : ouvre le panneau Lumières. Grisée si le pont ne répond pas. */
@Composable
private fun LightsButton(onClick: () -> Unit) {
    val status by Lights.status.collectAsState()
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.size(34.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val base = if (focused) Color(0xFF111114) else Color.White
            BulbGlyph(24.dp, if (status == HueStatus.OK) base else base.copy(alpha = 0.35f))
        }
    }
}

/**
 * Fabrique la capsule de verre de la première rangée quand la photo de fond change (hors du thread principal).
 * Composable à part : seul lui lit l'image affichée, l'écran ne se recompose pas pour autant.
 */
@Composable
private fun CapsuleBaker(image: () -> Wallpaper?, tileW: Dp, shelfH: Dp, sidePad: Dp, out: MutableState<ImageBitmap?>) {
    val wp = image()
    val d = LocalContext.current.resources.displayMetrics
    LaunchedEffect(wp, tileW, shelfH, sidePad) {
        if (wp == null) { out.value = null; return@LaunchedEffect }
        val x0 = ((sidePad - 16.dp).value * d.density).roundToInt()
        val y0 = ((shelfH + 6.dp).value * d.density).roundToInt()
        val w = d.widthPixels - 2 * x0
        val h = ((tileW.value / Config.CARD_ASPECT + 32f) * d.density).roundToInt()
        out.value = withContext(Dispatchers.Default) {
            bakeCapsule(wp, d.widthPixels, d.heightPixels, x0, y0, w, h, 30f * d.density, 1f * d.density)
        }
    }
}

/** Assombrissement de nuit, calé sur le vrai coucher du soleil. Recalculé une fois par minute, seul. */
@Composable
private fun NightDriver(weather: Weather?, out: MutableFloatState) {
    val now by rememberNow()
    val night = weather?.isNight(now) ?: (now.hour < 7 || now.hour >= 20)
    val a by animateFloatAsState(if (night) 0.3f else 0f, tween(3000), label = "night")
    SideEffect { out.floatValue = a }
}

/** Choix du titre affiché dans le Top Shelf. Seul ce bloc se recalcule quand la sélection bouge. */
@Composable
private fun ShelfDriver(
    focusedPkg: State<String?>,
    onShelfRow: Boolean,
    picks: Map<String, List<Pick>>,
    loadPick: suspend (Pick) -> Wallpaper?,
    pickImage: MutableState<Wallpaper?>,
    shownPick: MutableState<Pick?>,
    loadImages: Boolean,
) {
    // focusedPkg ne change que sur la première rangée : en descendant dans la grille, l'image reste en place
    val shelfPicks = focusedPkg.value?.let { picks[it] }.orEmpty()
    var pickIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(shelfPicks) { pickIndex = 0 }
    LaunchedEffect(shelfPicks, onShelfRow) {
        while (onShelfRow && shelfPicks.size > 1) {
            delay(10_000)
            pickIndex++
        }
    }
    val currentPick = shelfPicks.getOrNull(pickIndex % maxOf(1, shelfPicks.size))
    LaunchedEffect(currentPick, loadImages) {
        if (!loadImages) pickImage.value = null // fond fixe ou uni : on n'affiche que le titre et le texte
        // On attend que la télécommande se pose : ni chargement ni fondu pendant un défilement rapide
        // (y compris le retour au fond d'écran quand l'appli survolée n'a pas de titre tendance)
        delay(700)
        if (currentPick == null) {
            pickImage.value = null
            shownPick.value = null
            return@LaunchedEffect
        }
        if (!loadImages) {
            shownPick.value = currentPick
            return@LaunchedEffect
        }
        loadPick(currentPick)?.let {
            pickImage.value = it
            shownPick.value = currentPick
        }
    }
}

@Composable
private fun Backdrop(
    image: () -> Wallpaper?, blurAlpha: () -> Float, nightAlpha: () -> Float, reduced: Boolean, solid: Boolean,
    scrim: () -> Boolean,
) {
    // Fond uni sombre : un seul aplat, ni photo, ni fondu, ni dégradé
    if (solid) {
        Box(Modifier.fillMaxSize().background(SolidBackdrop))
        return
    }
    val wallpaper = image()
    // Ne dessiner que les images utiles : la nette disparaît sous la floutée quand on est dans la grille
    val anyBlur by remember { derivedStateOf { blurAlpha() > 0.001f } }
    val fullBlur by remember { derivedStateOf { blurAlpha() > 0.999f } }
    Box(
        Modifier
            .fillMaxSize()
            // Dégradé du bas, assombrissement de la grille et voile de nuit fusionnés en un seul calque
            .drawWithContent {
                drawContent()
                val dark = 1f - (1f - 0.35f * blurAlpha()) * (1f - nightAlpha())
                val bottom = 1f - (1f - 0.7f) * (1f - dark)
                // Le haut (40 %) est uni : une couleur pleine + un dégradé à 2 points coûtent moins
                // au GPU de la TV qu'un seul dégradé à 3 points sur tout l'écran
                val split = size.height * 0.4f
                if (dark > 0f) drawRect(Color.Black.copy(alpha = dark), size = Size(size.width, split))
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = dark),
                        1f to Color.Black.copy(alpha = bottom),
                        startY = split,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, split),
                    size = Size(size.width, size.height - split),
                )
                // Ombre sous le titre et l'heure : sur toute la hauteur de l'écran, donc sans bord net entre la
                // Top Shelf et la première rangée ; inutile une fois dans la grille (fond déjà assombri et flou)
                if (scrim() && blurAlpha() < 0.5f) {
                    val w = size.width * 0.55f
                    drawRect(
                        Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.5f), 1f to Color.Transparent, startX = 0f, endX = w),
                        size = Size(w, size.height),
                    )
                }
            }
    ) {
        if (wallpaper == null) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1C2B4B), Color(0xFF35204A), Color(0xFF0D0D12)),
                        start = Offset.Zero, end = Offset.Infinite,
                    )
                )
            )
        } else {
            Fade(wallpaper, animationSpec = if (reduced) snap() else tween(Config.SHELF_FADE_MS)) { wp ->
                Box(Modifier.fillMaxSize()) {
                    if (!fullBlur) Image(wp.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (anyBlur) {
                        Image(
                            wp.blurred, null,
                            Modifier.fillMaxSize().graphicsLayer {
                                alpha = blurAlpha()
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            },
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopShelf(height: Dp, weather: Weather?, reduced: Boolean, onLights: () -> Unit, pickProvider: () -> Pick?) {
    val pick = pickProvider()
    val now by rememberNow()
    val date = now.format(DateFormat).replaceFirstChar { it.titlecase(Locale.FRANCE) }
    Box(Modifier.fillMaxWidth().height(height)) {
        // Titre tendance de l'appli sélectionnée, en haut à gauche
        Fade(pick, animationSpec = if (reduced) snap() else tween(Config.SHELF_FADE_MS), modifier = Modifier.matchParentSize()) { p ->
            if (p != null) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(start = 52.dp, top = 40.dp).width(560.dp)) {
                        Text(
                            "TENDANCE SUR ${p.provider.uppercase(Locale.FRANCE)}",
                            style = TextStyle(
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
                                color = Color.White.copy(alpha = 0.8f), shadow = TextShadow,
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            p.title,
                            style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White, shadow = TextShadow),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (p.overview.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                p.overview,
                                style = TextStyle(fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f), shadow = TextShadow),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Données TMDB",
                            style = TextStyle(fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f)),
                        )
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 52.dp, bottom = 4.dp)) {
            Text(
                now.format(TimeFormat),
                style = TextStyle(fontSize = 84.sp, fontWeight = FontWeight.ExtraLight, color = Color.White, shadow = TextShadow),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val info = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.92f), shadow = TextShadow)
                Text(date, style = info)
                if (weather != null) {
                    Spacer(Modifier.width(16.dp))
                    WeatherGlyph(weather, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${weather.temp}°  ${weather.kind.label}", style = info)
                }
                Spacer(Modifier.width(18.dp))
                LightsButton(onLights)
            }
        }
        // Météo des prochaines heures, en bas à droite
        val next = weather?.hourly.orEmpty().filter { it.time.isAfter(now) }.take(5)
        if (next.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                val small = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f), shadow = TextShadow)
                next.forEach { h ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${h.time.hour} h", style = small)
                        Spacer(Modifier.height(6.dp))
                        WeatherGlyph(h.weather, 22.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("${h.weather.temp}°", style = small.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTile(
    app: TvApp,
    width: Dp,
    isMoving: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMove: (Int) -> Unit,
    onStopMoving: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val wiggle = if (isMoving) {
        rememberInfiniteTransition(label = "wiggle").animateFloat(
            -1.4f, 1.4f, infiniteRepeatable(tween(140, easing = LinearEasing), RepeatMode.Reverse), label = "rot",
        ).value
    } else 0f
    // Pas de nom sous la tuile (choix d'Alban, 06/11) : le logo suffit
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Config.CARD_ASPECT)
                .graphicsLayer { rotationZ = wiggle }
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .onPreviewKeyEvent { e ->
                    if (!isMoving) return@onPreviewKeyEvent false
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent e.key != Key.Back
                    when (e.key) {
                        Key.DirectionLeft -> onMove(-1)
                        Key.DirectionRight -> onMove(1)
                        Key.DirectionUp -> onMove(-Config.COLUMNS)
                        Key.DirectionDown -> onMove(Config.COLUMNS)
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> onStopMoving()
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = Config.FOCUS_SCALE, pressedScale = 1.1f),
            // Pas d'ombre portée : elle coûte trop cher au GPU de la TV, un liseré suffit
            glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
            border = ClickableSurfaceDefaults.border(
                // Liseré à la couleur signature de l'appli (tirée de son logo au chargement : aucun calcul ici)
                focusedBorder = Border(BorderStroke(2.dp, Color(app.accent).copy(alpha = 0.9f)), shape = RoundedCornerShape(14.dp)),
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF2B2B30),
                focusedContainerColor = Color(0xFF34343A),
                pressedContainerColor = Color(0xFF34343A),
            ),
        ) {
            if (app.banner != null) {
                Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (app.icon != null) {
                        Image(app.icon, app.label, Modifier.size(48.dp))
                    } else {
                        Text(app.label, color = Color.White, fontSize = 14.sp, maxLines = 1)
                    }
                }
            }
            // Le reflet qui passe sur la tuile sélectionnée
            if (focused) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.22f),
                            0.45f to Color.Transparent,
                            start = Offset.Zero, end = Offset(600f, 400f),
                        )
                    )
                )
            }
        }
    }
}

/**
 * Rangée « Reprendre la lecture » : jusqu'à trois cartes de deux colonnes (vignette, titre, appli, avancement).
 * OK relance l'appli, appui long retire la carte.
 */
@Composable
private fun ResumeRow(
    items: List<ResumeItem>,
    appFor: (String) -> TvApp?,
    tileW: Dp, gap: Dp, sidePad: Dp,
    requester: (String) -> FocusRequester,
    onFocused: () -> Unit,
    onClick: (ResumeItem) -> Unit,
    onLongClick: (ResumeItem) -> Unit,
) {
    val cardW = tileW * 2 + gap
    val cardH = tileW / Config.CARD_ASPECT
    Column(Modifier.fillMaxWidth().padding(start = sidePad, end = sidePad, top = 16.dp)) {
        Text(
            "Reprendre la lecture",
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            style = TextStyle(
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.75f), shadow = TextShadow,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            items.take(3).forEach { item ->
                val app = appFor(item.pkg)
                Surface(
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    modifier = Modifier
                        .width(cardW).height(cardH)
                        .focusRequester(requester(item.pkg))
                        .onFocusChanged { if (it.isFocused) onFocused() },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)), shape = RoundedCornerShape(14.dp)),
                    ),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF2B2B30), focusedContainerColor = Color(0xFF34343A),
                    ),
                ) {
                    Row(Modifier.fillMaxSize()) {
                        // Vignette : l'image publiée par l'appli, sinon sa bannière, sinon son icône
                        Box(Modifier.width(cardH * 16f / 9f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            val art = item.art ?: app?.banner
                            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else app?.icon?.let { Image(it, null, Modifier.size(36.dp)) }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                item.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(app?.label, item.subtitle.takeIf { it.isNotBlank() }).joinToString(" · "),
                                color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.weight(1f))
                            if (item.progress > 0f) {
                                Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.2f))) {
                                    Box(Modifier.fillMaxWidth(item.progress).fillMaxHeight().background(Color.White.copy(alpha = 0.9f)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Tuile « Autres applis » (dossier) : pas une vraie appli, juste une case de la grille avec une icône dessinée une fois
private const val FOLDER_PKG = "folder:other"

private val FolderApp: TvApp by lazy {
    val bmp = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt() }
    for (i in 0..1) for (j in 0..1) {
        val x = 12f + i * 38f
        val y = 12f + j * 38f
        c.drawRoundRect(android.graphics.RectF(x, y, x + 34f, y + 34f), 8f, 8f, p)
    }
    TvApp(FOLDER_PKG, "Autres applis", null, bmp.asImageBitmap(), Intent())
}

/** Ce que le menu d'options sait faire ; c'est MainActivity qui lance les commandes. */
class HomeOptionsActions(
    val enableSafetyNet: () -> Unit,
    val enableScreensaver: () -> Unit,
    val simulateCrash: () -> Unit,
    val restoreGoogle: () -> Unit,
    val grantMediaAccess: () -> Unit,
    val setBackHold: (Boolean) -> Unit,
)

