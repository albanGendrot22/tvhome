package fr.alban.tvhome

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Le Crossfade de Compose, à un détail près : la transparence est appliquée à chaque dessin
 * au lieu de passer par un calque hors écran. Sur le Mali-470 de la TV, ce calque plein écran
 * coûtait un remplissage 1080p de plus à chaque image du fondu.
 */
@Composable
fun <T> Fade(
    targetState: T,
    animationSpec: FiniteAnimationSpec<Float>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val transition = updateTransition(targetState, label = "fade")
    val visible = remember(transition) { mutableStateListOf(transition.currentState) }
    if (transition.currentState == transition.targetState) {
        // Fondu terminé : on ne garde que l'état affiché
        if (visible.size != 1 || visible[0] != transition.targetState) {
            visible.removeAll { it != transition.targetState }
        }
    }
    if (transition.targetState !in visible) visible.add(transition.targetState)
    Box(modifier) {
        visible.forEach { state ->
            key(state) {
                val alpha by transition.animateFloat({ animationSpec }, label = "alpha") { if (it == state) 1f else 0f }
                Box(Modifier.graphicsLayer {
                    this.alpha = alpha
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }) { content(state) }
            }
        }
    }
}
