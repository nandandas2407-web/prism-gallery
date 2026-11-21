package com.prism.gallery.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.prism.gallery.ui.theme.GlassStyle
import com.prism.gallery.ui.theme.LocalThemeSettings
import kotlin.math.cos
import kotlin.math.sin

/** Soft gradient orbs that slowly drift. Colours, glow, blur and motion all come from the theme. */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val s = LocalThemeSettings.current
    val c1 = MaterialTheme.colorScheme.primary
    val c2 = MaterialTheme.colorScheme.tertiary
    val c3 = Color(s.accent3)
    val animate = s.animatedBackdrop && !s.reduceMotion
    val phase = rememberInfiniteTransition(label = "liquid").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing)),
        label = "phase",
    )
    val k = s.orbIntensity
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val p = if (animate) phase.value else 0f // read in draw phase only: no recomposition per frame
            val d = size.minDimension * .06f
            val soft = .55f + s.blur * .45f // blur slider = how far each orb fades out
            fun orb(color: Color, alpha: Float, radius: Float, x: Float, y: Float, seed: Float) {
                val r = size.minDimension * radius * (1f + soft)
                val c = Offset(size.width * x + sin(p + seed) * d, size.height * y + cos(p * 1.3f + seed) * d)
                val a = (alpha * k).coerceIn(0f, 1f)
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a), color.copy(alpha = a * .35f), Color.Transparent), c, r), r, c)
            }
            orb(c1, .90f, .55f, .15f, .12f, 0f)
            orb(c2, .65f, .50f, .90f, .45f, 2f)
            orb(c3, .50f, .60f, .30f, .95f, 4f)
        }
        content()
    }
}

/** Translucent liquid-glass panel. Look is driven by [com.prism.gallery.ui.theme.GlassStyle], glow and shine settings. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LocalThemeSettings.current.cornerRadius.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val s = LocalThemeSettings.current
    val alpha = (s.glassOpacity + if (s.highContrast) .35f else 0f).coerceAtMost(.95f)
    val tint = MaterialTheme.colorScheme.surface
    val accent = MaterialTheme.colorScheme.primary
    val accent2 = MaterialTheme.colorScheme.tertiary
    val glow = s.borderGlow
    val hair = if (s.highContrast) 2.dp else 1.dp

    val fill: Brush = when (s.style) {
        GlassStyle.CRYSTAL -> Brush.linearGradient(listOf(tint.copy(alpha = (alpha * 1.1f).coerceAtMost(1f)), tint.copy(alpha = alpha * .35f)))
        GlassStyle.NEON -> SolidColor(tint.copy(alpha = (alpha * 1.25f).coerceAtMost(1f)))
        GlassStyle.MINIMAL -> SolidColor(tint.copy(alpha = alpha))
        GlassStyle.FROSTED -> Brush.linearGradient(listOf(tint.copy(alpha = (alpha * 1.4f).coerceAtMost(1f)), tint.copy(alpha = alpha * .6f)))
    }
    val shineK = when (s.style) {
        GlassStyle.CRYSTAL -> s.shine * 1.6f
        GlassStyle.MINIMAL -> 0f
        else -> s.shine
    }
    var m = modifier.clip(shape).background(fill)
    if (shineK > 0f) {
        m = m.background(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = (.22f * shineK).coerceIn(0f, .5f)),
                .45f to Color.Transparent,
                1f to Color.Transparent,
            ),
        )
    }
    m = when (s.style) {
        GlassStyle.NEON -> m
            .border(hair * 3f, accent.copy(alpha = .18f * glow), shape)
            .border(hair * 1.5f, Brush.linearGradient(listOf(accent.copy(alpha = glow), accent2.copy(alpha = glow))), shape)
        GlassStyle.CRYSTAL -> m.border(
            hair,
            Brush.linearGradient(listOf(Color.White.copy(alpha = .7f * glow), accent.copy(alpha = .3f * glow), Color.White.copy(alpha = .5f * glow))),
            shape,
        )
        GlassStyle.MINIMAL -> m.border(hair, accent.copy(alpha = .25f * glow), shape)
        GlassStyle.FROSTED -> m.border(
            hair,
            Brush.linearGradient(listOf(accent.copy(alpha = .85f * glow), Color.White.copy(alpha = .15f), accent.copy(alpha = .45f * glow))),
            shape,
        )
    }
    Box(m, content = content)
}

/** 48dp minimum target, TalkBack label, haptic tick. */
fun Modifier.hapticClickable(description: String, onClick: () -> Unit): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    this
        .minimumInteractiveComponentSize()
        .semantics { contentDescription = description }
        .clickable(role = Role.Button) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Shared-element hook: thumbnails in the grid morph into the viewer. */
@Composable
fun Modifier.sharedImage(key: Any): Modifier {
    val shared = LocalSharedScope.current
    val anim = LocalAnimScope.current
    return if (shared != null && anim != null) {
        with(shared) { this@sharedImage.sharedElement(rememberSharedContentState(key), anim) }
    } else {
        this
    }
}
