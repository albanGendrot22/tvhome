@file:OptIn(ExperimentalTvMaterial3Api::class)

package fr.alban.tvhome

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

// Adapté de tvOS Launcher (techgenius1), Apache 2.0 : « Scenes » (paysage plein écran et horloge géante)
// et mode ambiant ; ici une seule image FIXE, sans animation, pour rester léger sur le GPU de cette TV.

private val SceneTime = DateTimeFormatter.ofPattern("HH:mm")
private val SceneDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

/**
 * Plein écran sur la photo d'accueil (jour ou nuit) : mode Art = la photo seule, scène = la photo avec une horloge
 * géante. Aucune animation : l'image est dessinée une fois, seule l'horloge change à la minute. N'importe quelle
 * touche ferme. Vit dans sa propre fenêtre : fermée, elle n'existe pas.
 */
@Composable
fun SceneDialog(wallpaper: Wallpaper?, weather: Weather?, art: Boolean, onDismiss: () -> Unit) {
    val now by rememberNow()
    val night = weather?.isNight(now) ?: (now.hour < 7 || now.hour >= 20)
    val focus = remember { FocusRequester() }
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
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown) onDismiss()
                    true
                }
        ) {
            if (wallpaper != null) Image(wallpaper.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (!art) {
                // Pied de page sombre pour que l'horloge reste lisible sur une photo claire ; plus sombre la nuit
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.5f).background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = if (night) 0.75f else 0.55f),
                        )
                    )
                )
                // Un léger décalage à chaque minute (jusqu'à 30 dp) pour ne pas marquer l'écran
                val shift = (now.minute % 6) * 6
                Column(Modifier.align(Alignment.BottomStart).padding(start = (72 + shift).dp, bottom = (48 + shift).dp)) {
                    val tint = if (night) Color(0xFFFFC9A3) else Color.White
                    Text(
                        now.format(SceneTime),
                        style = TextStyle(
                            fontSize = 150.sp, fontWeight = FontWeight.ExtraLight, color = tint,
                            shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 3f), 18f),
                        ),
                    )
                    Text(
                        now.format(SceneDate).replaceFirstChar { it.titlecase(Locale.FRANCE) },
                        style = TextStyle(
                            fontSize = 26.sp, fontWeight = FontWeight.Medium, color = tint.copy(alpha = 0.9f),
                            shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 2f), 12f),
                        ),
                    )
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(50)
            runCatching { focus.requestFocus() }
        }
    }
}
