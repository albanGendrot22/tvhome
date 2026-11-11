@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.alban.tvhome

import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PanelBg = Color(0xFF16161A)
// Teinte posée sur le verre dépoli : assez sombre pour garder le texte lisible, la photo floutée transparaît
private val PanelTint = Color(0xB316161A)
private val ItemBg = Color(0xFF26262C)

/**
 * Panneau "Lumières", façon centre de contrôle tvOS : le tiers droit de l'écran, opaque, qui glisse depuis
 * la droite (animation de position, pas de fondu). Il vit dans sa propre fenêtre : l'accueil derrière
 * ne se redessine pas, et tant qu'il est fermé il n'existe pas du tout.
 */
@Composable
fun LightsPanel(
    glass: () -> ImageBitmap?,
    onDismiss: () -> Unit,
    header: @Composable ColumnScope.(FocusRequester) -> Unit = {},
) {
    val slide = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            if (UiSettings.reducedAnimations.value) slide.snapTo(1f)
            else slide.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    val panelW = 380.dp
    val panelPx = with(LocalDensity.current) { panelW.roundToPx() }
    val metrics = LocalContext.current.resources.displayMetrics
    val screen = Size(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        DisposableEffect(Unit) {
            // Fenêtre collée à droite, sans voile sombre ni animation système : seul notre glissement se voit
            window.setGravity(Gravity.END)
            window.setLayout(panelPx, WindowManager.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setWindowAnimations(0)
            onDispose {}
        }
        LaunchedEffect(Unit) {
            if (UiSettings.reducedAnimations.value) slide.snapTo(0f)
            else slide.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
        }

        // La fenêtre couvre l'écran ; le panneau n'occupe que sa colonne de droite
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(panelW)
                    .graphicsLayer { translationX = slide.value * size.width }
                    // Verre dépoli : la photo floutée du fond, à sa place à l'écran (panneau collé à droite)
                    .drawBehind {
                        val img = glass()
                        if (img == null) drawRect(PanelBg)
                        else drawFrosted(img, Offset(screen.width - size.width, 0f), screen, PanelTint)
                    }
            ) {
                LightsContent(header)
            }
        }
    }
}

@Composable
private fun LightsContent(header: @Composable ColumnScope.(FocusRequester) -> Unit) {
    val ctx = LocalContext.current
    val status by Lights.status.collectAsState()
    var rooms by remember { mutableStateOf<List<HueRoom>?>(null) }
    var scenes by remember { mutableStateOf<List<HueScene>>(emptyList()) }
    var loaded by remember { mutableIntStateOf(0) }
    var sceneRoom by remember { mutableStateOf(Lights.salon(ctx)) }
    var pairing by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val hueFirst = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // État lu une seule fois à l'ouverture (et après une scène) : pas d'interrogation en boucle
    LaunchedEffect(loaded, status) {
        val c = Lights.config(ctx) ?: return@LaunchedEffect
        val r = HueBridge.rooms(c)
        if (r == null) { Lights.refreshStatus(ctx); return@LaunchedEffect }
        rooms = r
        scenes = HueBridge.scenes(c).orEmpty()
        r.forEach { Log.i("TvHomeHue", "Pièce : ${it.name} (${it.id}), ${it.lights.size} lumières") }
        scenes.forEach { Log.i("TvHomeHue", "Scène : ${it.name} (${it.id}) pièce ${it.roomId}") }
        if (sceneRoom == null) sceneRoom = r.firstOrNull()?.id
    }
    // Le focus part sur la première ligne du centre de contrôle ; seul l'appairage Hue le réclame ensuite
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { first.requestFocus() }
    }
    LaunchedEffect(pairing) {
        if (!pairing) return@LaunchedEffect
        delay(80)
        runCatching { hueFirst.requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Centre de contrôle", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        header(first)
        Spacer(Modifier.height(6.dp))
        Text("Lumières", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

        when {
            pairing || status == HueStatus.UNPAIRED && Lights.config(ctx) == null ->
                if (pairing) HuePairing(hueFirst, onDone = { pairing = false; loaded++ })
                else PanelButton("Associer le pont Hue", hueFirst) { pairing = true }
            status == HueStatus.UNREACHABLE -> {
                Note("Le pont Hue ne répond pas.")
                PanelButton("Réessayer", hueFirst) { Lights.refreshStatus(ctx); loaded++ }
            }
            rooms == null -> Note("Lecture des pièces…")
            else -> {
                val list = rooms!!
                Section("Pièces")
                list.forEachIndexed { i, room ->
                    RoomRow(
                        room,
                        if (i == 0) Modifier.focusRequester(hueFirst) else Modifier,
                        onFocused = { sceneRoom = room.id },
                    )
                }
                val roomScenes = scenes.filter { it.roomId == sceneRoom }
                if (roomScenes.isNotEmpty()) {
                    Section("Scènes · ${list.firstOrNull { it.id == sceneRoom }?.name.orEmpty()}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        roomScenes.forEach { s ->
                            Chip(s.name, onLongClick = { Lights.setMusicScene(ctx, s.id) }) {
                                Lights.recall(ctx, s.id)
                                scope.launch { delay(1_500); loaded++ } // relire les pièces une fois la scène posée
                            }
                        }
                    }
                    Note("Appui long sur une scène : elle devient la scène « Musique ».", small = true)
                }
                if (Lights.musicScene(ctx) != null) {
                    Section("Raccourcis")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("Musique") { Lights.music(ctx) }
                    }
                }
            }
        }
    }
}

/** Une pièce : OK allume ou éteint, gauche / droite règle la luminosité. */
@Composable
private fun RoomRow(room: HueRoom, modifier: Modifier, onFocused: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember(room) { mutableStateOf(room.on) }
    var bri by remember(room) { mutableStateOf(room.brightness.coerceIn(1f, 100f)) }
    var focused by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Job?>(null) }

    // Le pont n'accepte qu'environ un ordre par seconde pour une pièce : on n'envoie que la dernière valeur
    fun send() {
        pending?.cancel()
        pending = scope.launch {
            delay(350)
            val c = Lights.config(ctx) ?: return@launch
            room.groupedLight?.let { HueBridge.setGroup(c, it, on = on, brightness = if (on) bri else null) }
        }
    }

    Surface(
        onClick = { on = !on; send() },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { e ->
                val step = when (e.key) { Key.DirectionLeft -> -10f; Key.DirectionRight -> 10f; else -> return@onPreviewKeyEvent false }
                if (e.type == KeyEventType.KeyDown) {
                    bri = (bri + step).coerceIn(1f, 100f)
                    on = true
                    send()
                }
                true
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None),
        colors = ClickableSurfaceDefaults.colors(containerColor = ItemBg, focusedContainerColor = Color.White),
    ) {
        val fg = if (focused) Color(0xFF111114) else Color.White
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BulbGlyph(22.dp, if (on) Color(0xFFFFC94D) else fg.copy(alpha = 0.4f))
                Spacer(Modifier.width(12.dp))
                Text(room.name, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(if (on) "${bri.toInt()} %" else "Éteint", color = fg.copy(alpha = 0.7f), fontSize = 15.sp)
            }
            Spacer(Modifier.height(10.dp))
            // Barre de luminosité
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(fg.copy(alpha = 0.15f))) {
                if (on) Box(Modifier.fillMaxWidth(bri / 100f).fillMaxHeight().clip(CircleShape).background(fg.copy(alpha = 0.85f)))
            }
        }
    }
}

/**
 * Appairage : trouver le pont, demander d'appuyer sur son bouton, récupérer la clé,
 * puis choisir la pièce qui sert de "salon" pour les automatismes.
 */
@Composable
private fun HuePairing(first: FocusRequester, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableStateOf("Recherche du pont Hue…") }
    var rooms by remember { mutableStateOf<List<HueRoom>?>(null) }
    var failed by remember { mutableIntStateOf(0) }

    LaunchedEffect(failed) {
        val ip = HueBridge.discover(ctx)
        if (ip == null) { step = "Pont Hue introuvable sur le réseau."; return@LaunchedEffect }
        Log.i("TvHomeHue", "Pont trouvé : $ip, en attente du bouton")
        for (left in 60 downTo 1 step 2) {
            step = "Appuie sur le gros bouton rond du pont Hue.\n($ip · encore $left s)"
            val got = HueBridge.requestKey(ip)
            if (got != null) {
                Lights.savePairing(ctx, ip, got.first, got.second)
                Log.i("TvHomeHue", "Pont appairé")
                val c = Lights.config(ctx)!!
                rooms = HueBridge.rooms(c).orEmpty().onEach { Log.i("TvHomeHue", "Pièce : ${it.name} (${it.id})") }
                step = "C'est fait ! Quelle pièce est le salon ?"
                return@LaunchedEffect
            }
            delay(2_000)
        }
        step = "Le bouton n'a pas été pressé à temps."
    }

    Note(step)
    val list = rooms
    if (list == null) {
        PanelButton("Recommencer", first) { failed++ }
    } else {
        list.forEachIndexed { i, room ->
            PanelButton(room.name, if (i == 0) first else null) {
                Lights.setSalon(ctx, room.id)
                onDone()
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Note(text: String, small: Boolean = false) {
    Text(text, color = Color.White.copy(alpha = if (small) 0.45f else 0.8f), fontSize = if (small) 12.sp else 15.sp)
}

@Composable
private fun PanelButton(label: String, requester: FocusRequester?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = (if (requester != null) Modifier.focusRequester(requester) else Modifier).fillMaxWidth().height(48.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = ItemBg, contentColor = Color.White,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
        ),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Pastille (scène, raccourci). */
@Composable
private fun Chip(label: String, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = ItemBg, contentColor = Color.White,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
        ),
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
    }
}

/** Petite ampoule dessinée à la main, dans l'esprit des pictos météo. */
@Composable
fun BulbGlyph(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = s * 0.09f
        drawCircle(color, s * 0.27f, Offset(s * 0.5f, s * 0.38f), style = Stroke(width = w))
        drawRoundRect(color, Offset(s * 0.37f, s * 0.66f), Size(s * 0.26f, s * 0.08f), CornerRadius(w / 2))
        drawRoundRect(color, Offset(s * 0.4f, s * 0.8f), Size(s * 0.2f, s * 0.08f), CornerRadius(w / 2))
        drawLine(color, Offset(s * 0.44f, s * 0.6f), Offset(s * 0.44f, s * 0.64f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.56f, s * 0.6f), Offset(s * 0.56f, s * 0.64f), w, StrokeCap.Round)
    }
}
