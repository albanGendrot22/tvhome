@file:OptIn(ExperimentalTvMaterial3Api::class)

package fr.alban.tvhome

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SourceKind { ANTENNA, HDMI, DEVICE }

/** Une entrée de la TV : l'antenne ou une prise HDMI (avec le nom de l'appareil branché si la TV le connaît). */
class TvSource(
    val id: String,
    val name: String,
    val subtitle: String?,
    val kind: SourceKind,
    val intents: List<Intent>,
) {
    val hideKey get() = "src:$id"
}

object SourceRepository {

    suspend fun load(ctx: Context): List<TvSource> = withContext(Dispatchers.IO) {
        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            ?: return@withContext emptyList()
        val inputs = runCatching { tim.tvInputList }.getOrDefault(emptyList())
            .filter { runCatching { !it.isHidden(ctx) }.getOrDefault(true) }
        val hidden = Prefs.hidden(ctx)
        val out = mutableListOf<TvSource>()

        // Antenne : le premier tuner suffit, la TV gère elle-même TNT / satellite
        if (Config.SHOW_ANTENNA) inputs.firstOrNull { it.type == TvInputInfo.TYPE_TUNER && it.parentId == null }?.let { tuner ->
            out += TvSource(
                id = tuner.id,
                name = "TV",
                subtitle = "Antenne",
                kind = SourceKind.ANTENNA,
                intents = listOf(
                    Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(tuner.id)),
                    Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI),
                ),
            )
        }

        // Prises HDMI physiques, dans l'ordre de la TV (HW4, HW5, HW6...)
        val ports = inputs
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null }
            .sortedWith(compareBy({ it.id.length }, { it.id }))
        ports.forEachIndexed { i, port ->
            val portName = port.customLabel(ctx) ?: "HDMI ${i + 1}"
            // Appareil reconnu par HDMI-CEC sur cette prise (ex. "Apple TV")
            val device = inputs.firstOrNull { it.parentId == port.id }
                ?.let { runCatching { it.loadLabel(ctx).toString() }.getOrNull() }
                ?.takeIf { it.isNotBlank() && !it.equals(portName, ignoreCase = true) }
            out += TvSource(
                id = port.id,
                name = device ?: portName,
                subtitle = if (device != null) portName else null,
                kind = if (device != null) SourceKind.DEVICE else SourceKind.HDMI,
                intents = listOf(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(port.id))),
            )
        }
        out.filter { it.hideKey !in hidden }
    }

    private fun TvInputInfo.customLabel(ctx: Context): String? =
        runCatching { loadCustomLabel(ctx)?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Essaie chaque intention dans l'ordre, puis ouvre l'appli TV du fabricant en dernier recours. */
    fun open(ctx: Context, source: TvSource): Boolean {
        for (intent in source.intents) {
            val ok = runCatching { ctx.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (ok) return true
        }
        val fallback = ctx.packageManager.getLeanbackLaunchIntentForPackage("com.mediatek.wwtv.tvcenter")
            ?: return false
        return runCatching { ctx.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}

/** Grosse tuile de source : verre dépoli au repos, blanche quand elle est sélectionnée, comme sur tvOS. */
@Composable
fun SourceTile(
    source: TvSource,
    width: Dp,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "src")
    val content = lerp(Color.White, Color(0xFF111114), t)

    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .width(width)
            .aspectRatio(16f / 9f)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f, pressedScale = 1.04f),
        // Pas d'ombre portée : elle coûte trop cher au GPU de la TV, un liseré suffit
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.6f)), shape = RoundedCornerShape(18.dp)),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.14f),
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White,
        ),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SourceGlyph(source.kind, 40.dp, content)
            Spacer(Modifier.height(8.dp))
            Text(
                source.name,
                color = content,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                source.subtitle ?: " ",
                color = content.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SourceGlyph(kind: SourceKind, size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = s * 0.075f
        val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            SourceKind.HDMI -> {
                // Le profil d'une prise HDMI : large en haut, coins biseautés en bas
                val p = Path().apply {
                    moveTo(s * 0.12f, s * 0.32f); lineTo(s * 0.88f, s * 0.32f)
                    lineTo(s * 0.88f, s * 0.52f); lineTo(s * 0.74f, s * 0.68f)
                    lineTo(s * 0.26f, s * 0.68f); lineTo(s * 0.12f, s * 0.52f); close()
                }
                drawPath(p, color, style = stroke)
                drawLine(color, Offset(s * 0.3f, s * 0.47f), Offset(s * 0.7f, s * 0.47f), w, StrokeCap.Round)
            }
            SourceKind.DEVICE -> {
                // Un boîtier vu de face, avec sa petite LED
                drawRoundRect(
                    color, Offset(s * 0.12f, s * 0.36f), Size(s * 0.76f, s * 0.3f),
                    CornerRadius(s * 0.09f), style = stroke,
                )
                drawCircle(color, s * 0.04f, Offset(s * 0.74f, s * 0.51f))
                drawLine(color, Offset(s * 0.26f, s * 0.78f), Offset(s * 0.74f, s * 0.78f), w, StrokeCap.Round)
            }
            SourceKind.ANTENNA -> {
                // Une antenne émettrice : un mât et deux ondes de chaque côté
                val c = Offset(s * 0.5f, s * 0.36f)
                drawCircle(color, s * 0.06f, c)
                drawLine(color, Offset(c.x, c.y + s * 0.08f), Offset(s * 0.36f, s * 0.88f), w, StrokeCap.Round)
                drawLine(color, Offset(c.x, c.y + s * 0.08f), Offset(s * 0.64f, s * 0.88f), w, StrokeCap.Round)
                for (r in listOf(0.17f, 0.3f)) {
                    val rr = s * r
                    val tl = Offset(c.x - rr, c.y - rr)
                    val sz = Size(rr * 2, rr * 2)
                    drawArc(color, 140f, 80f, false, tl, sz, style = stroke)
                    drawArc(color, -40f, 80f, false, tl, sz, style = stroke)
                }
            }
        }
    }
}
