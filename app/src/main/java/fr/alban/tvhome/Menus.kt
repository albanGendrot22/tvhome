@file:OptIn(ExperimentalTvMaterial3Api::class)

package fr.alban.tvhome

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

// Adapté de tvOS Launcher (techgenius1), Apache 2.0 : disposition des réglages façon tvOS
// (titre centré, grande icône et aide à gauche, liste de pastilles à droite, ligne choisie en blanc plein).

// Teinte posée sur le faux verre : la photo floutée précalculée transparaît, le texte reste lisible
private val MenuTint = Color(0xB3101014)
private val MenuBg = Color(0xFF16161A)
private val RowText = Color(0xFF111114)

/**
 * Écran de menu plein écran, dans sa propre fenêtre : tant qu'il est fermé il n'existe pas, et l'accueil derrière
 * ne se redessine pas. Fond = photo floutée précalculée, opaque (aucun flou temps réel, aucun calque).
 * [content] reçoit le demandeur de focus de la première ligne.
 */
@Composable
fun GlassScreen(
    glass: () -> ImageBitmap?,
    title: String,
    icon: String,
    help: String,
    pageKey: Any,
    onBack: () -> Unit,
    content: @Composable ColumnScope.(FocusRequester) -> Unit,
) {
    val metrics = LocalContext.current.resources.displayMetrics
    val screen = Size(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
    val first = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        DisposableEffect(Unit) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setWindowAnimations(0)
            onDispose {}
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                val img = glass()
                if (img == null) drawRect(MenuBg) else drawFrosted(img, Offset.Zero, screen, MenuTint)
            }
        ) {
            Text(
                title,
                modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
            Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 92.dp, bottom = 36.dp)) {
                Column(
                    Modifier.weight(0.42f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(132.dp).clip(RoundedCornerShape(30.dp)).background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(icon, fontSize = 56.sp, color = Color.White) }
                    Text(
                        help,
                        modifier = Modifier.padding(top = 16.dp).width(260.dp),
                        color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.width(20.dp))
                Column(
                    Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) { content(first) }
            }
        }
        // À chaque changement de page, le focus revient sur la première ligne
        LaunchedEffect(pageKey) {
            delay(50)
            runCatching { first.requestFocus() }
        }
    }
}

/** Une pastille de la liste : libellé à gauche, valeur et chevron à droite. Choisie = blanc plein, texte sombre. */
@Composable
fun MenuRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    chevron: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(40.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.13f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = RowText,
            pressedContainerColor = Color.White,
            pressedContentColor = RowText,
        ),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value != null) Text(value, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp), maxLines = 1)
            if (chevron) Text("›", fontSize = 20.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/**
 * Carte de menu centrée (menu d'une appli, renommer…) : même faux verre, posé à sa place à l'écran.
 * Dans sa propre fenêtre, comme [GlassScreen].
 */
@Composable
fun GlassCard(
    glass: () -> ImageBitmap?,
    width: Dp,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val metrics = LocalContext.current.resources.displayMetrics
    val screen = Size(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
    var origin by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        DisposableEffect(Unit) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setWindowAnimations(0)
            onDispose {}
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(width)
                    .clip(RoundedCornerShape(22.dp))
                    .onGloballyPositioned { origin = it.positionInWindow() }
                    .drawBehind {
                        val img = glass()
                        if (img == null) drawRect(MenuBg) else drawFrosted(img, origin, screen, MenuTint)
                    }
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    }
}

/** Menu d'une appli (appui long sur OK) : Renommer, Déplacer, Autres applis, Masquer, Infos, Réinitialiser. */
@Composable
fun AppMenuCard(
    glass: () -> ImageBitmap?,
    title: String,
    entries: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    GlassCard(glass, 330.dp, onDismiss) {
        Text(
            title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        entries.forEachIndexed { i, (label, action) ->
            MenuRow(label, if (i == 0) Modifier.focusRequester(first) else Modifier) { action() }
        }
        MenuRow("Annuler", onClick = onDismiss)
        LaunchedEffect(Unit) {
            delay(50)
            runCatching { first.requestFocus() }
        }
    }
}

/** Renommer une appli : champ de saisie (clavier de la TV), puis Valider / Nom d'origine / Annuler. */
@Composable
fun RenameCard(
    glass: () -> ImageBitmap?,
    current: String,
    onDone: (String) -> Unit,
    onOriginal: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    // Le clavier de la TV prend les flèches tant qu'il est ouvert : on ne l'ouvre que sur demande (ligne « Nom »),
    // la touche ✓ du clavier valide. Le reste de la carte est fait de boutons ordinaires.
    var editing by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    GlassCard(glass, 360.dp, onDismiss) {
        Text("Renommer", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        if (editing) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(field),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { inner() }
                },
            )
            LaunchedEffect(Unit) {
                delay(50)
                runCatching { field.requestFocus() }
            }
        } else {
            MenuRow("Nom", Modifier.focusRequester(first), value = text) { editing = true }
            MenuRow("Valider") { onDone(text) }
            MenuRow("Nom d'origine", onClick = onOriginal)
            MenuRow("Annuler", onClick = onDismiss)
            LaunchedEffect(Unit) {
                delay(50)
                runCatching { first.requestFocus() }
            }
        }
    }
}

/** Dossier « Autres applis » : la liste des applis rangées hors de l'accueil. Appui long = menu de l'appli. */
@Composable
fun FolderScreen(
    glass: () -> ImageBitmap?,
    apps: List<TvApp>,
    onLaunch: (TvApp) -> Unit,
    onMenu: (TvApp) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassScreen(glass, "Autres applis", "▦", "Les applis rangées hors de l'accueil. Appui long sur une appli pour la remettre sur l'accueil.", "folder", onDismiss) { first ->
        apps.forEachIndexed { i, app ->
            MenuRow(app.label, if (i == 0) Modifier.focusRequester(first) else Modifier, onLongClick = { onMenu(app) }) {
                onDismiss(); onLaunch(app)
            }
        }
        if (apps.isEmpty()) MenuRow("Aucune appli", Modifier.focusRequester(first)) { onDismiss() }
    }
}

/** Réglages de l'accueil façon tvOS : une liste principale, puis des pages de détail (Retour = page précédente). */
@Composable
fun SettingsMenu(
    actions: HomeOptionsActions,
    glass: () -> ImageBitmap?,
    hiddenApps: List<TvApp>,
    onLights: () -> Unit,
    onUnhide: (TvApp) -> Unit,
    onUnhideAll: () -> Unit,
    onResetLayout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val stack = remember { mutableStateListOf("root") }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val page = stack.last()
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onDismiss() }
    fun open(p: String) { stack.add(p) }

    val backdrop by UiSettings.backdropMode
    val reduced by UiSettings.reducedAnimations
    val glassBand by UiSettings.glassBand
    val navSound by UiSettings.navSound
    val backHold by UiSettings.backHold
    val scene by UiSettings.scene
    val onOff = { b: Boolean -> if (b) "Activé" else "Désactivé" }

    val (title, icon, help) = when (page) {
        "look" -> Triple("Apparence", "🎨", "Fond de l'accueil, animations, verre dépoli et son de navigation.")
        "apps" -> Triple("Applis", "▦", "Appui long sur une appli : renommer, déplacer, ranger dans Autres applis, masquer.")
        "hidden" -> Triple("Applis masquées", "🔒", "Les applis masquées de l'accueil. Choisis-en une pour la réafficher.")
        "system" -> Triple(
            "Système", "⚙",
            "Filet de sécurité et retour à l'accueil Google. La première fois, la TV demande d'autoriser le débogage : coche « Toujours autoriser ».",
        )
        else -> Triple("Réglages", "⌂", "Personnalise ton accueil. Appui long sur une appli pour la déplacer ou la masquer.")
    }
    GlassScreen(glass, title, icon, help, page, ::back) { first ->
        when (page) {
            "look" -> {
                MenuRow(
                    "Fond d'accueil", Modifier.focusRequester(first),
                    value = when (backdrop) {
                        UiSettings.BACKDROP_PHOTO -> "Photo fixe"
                        UiSettings.BACKDROP_SOLID -> "Fond uni sombre"
                        else -> "Top Shelf dynamique"
                    },
                ) { UiSettings.setBackdropMode((backdrop + 1) % 3) }
                MenuRow("Animations", value = if (reduced) "Réduites" else "Normales") { UiSettings.setReducedAnimations(!reduced) }
                MenuRow("Capsule de verre autour des applis", value = onOff(glassBand)) { UiSettings.setGlassBand(!glassBand) }
                MenuRow("Son de navigation", value = onOff(navSound)) { UiSettings.setNavSound(!navSound) }
                MenuRow("Scène (paysage et horloge géante)", value = onOff(scene)) { UiSettings.setScene(!scene) }
            }
            "apps" -> {
                MenuRow("Applis masquées", Modifier.focusRequester(first), value = "${hiddenApps.size}", chevron = true) { open("hidden") }
                MenuRow(
                    if (confirmReset) "Confirmer : ordre, dossier et noms à zéro" else "Réinitialiser la disposition",
                ) { if (confirmReset) { confirmReset = false; onResetLayout(); onDismiss() } else confirmReset = true }
            }
            "hidden" -> {
                if (hiddenApps.isEmpty()) {
                    MenuRow("Aucune appli masquée", Modifier.focusRequester(first)) { back() }
                } else {
                    hiddenApps.forEachIndexed { i, app ->
                        MenuRow(app.label, if (i == 0) Modifier.focusRequester(first) else Modifier, value = "Réafficher") { onUnhide(app) }
                    }
                    MenuRow("Tout réafficher") { onDismiss(); onUnhideAll() }
                }
            }
            "system" -> {
                MenuRow(
                    "Appui long sur Retour", Modifier.focusRequester(first),
                    value = if (backHold) "Centre de contrôle" else "Désactivé",
                ) { actions.setBackHold(!backHold) }
                // « Reprendre la lecture » : Android n'autorise la lecture des sessions média qu'à un écouteur validé
                val ctx = LocalContext.current
                val mediaOk = remember {
                    val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
                    runCatching { nm.isNotificationListenerAccessGranted(android.content.ComponentName(ctx, MediaListener::class.java)) }
                        .getOrDefault(false)
                }
                MenuRow("Reprendre la lecture", value = if (mediaOk) "Autorisé" else "À autoriser") {
                    if (!mediaOk) { onDismiss(); actions.grantMediaAccess() }
                }
                MenuRow("Activer le filet de sécurité") { onDismiss(); actions.enableSafetyNet() }
                MenuRow("Tester : simuler un plantage") { onDismiss(); actions.simulateCrash() }
                MenuRow(
                    if (confirmRestore) "Confirmer le retour à l'accueil Google" else "Revenir à l'accueil Google",
                ) { if (confirmRestore) { onDismiss(); actions.restoreGoogle() } else confirmRestore = true }
            }
            else -> {
                MenuRow("Apparence", Modifier.focusRequester(first), chevron = true) { open("look") }
                MenuRow("Applis", chevron = true) { open("apps") }
                MenuRow("Lumières Hue", chevron = true) { onLights() }
                MenuRow("Économiseur d'écran", value = "Photos", chevron = true) { onDismiss(); actions.enableScreensaver() }
                MenuRow("Système", chevron = true) { open("system") }
            }
        }
    }
}
