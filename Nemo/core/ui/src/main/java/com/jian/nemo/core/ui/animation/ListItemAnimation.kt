package com.jian.nemo.core.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp

/**
 * 列表项入场动画 Modifier 扩展
 *
 * 为 LazyColumn 中的列表项提供统一的入场动画效果：
 * - 淡入 (alpha: 0 → 1)
 * - 缩放 (scale: 0.85 → 1.0)
 * - 上移 (translationY: 30px → 0)
 * - 弹性曲线 (Spring.DampingRatioLowBouncy)
 */
fun Modifier.animateListItem(): Modifier = composed {
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    this.graphicsLayer {
        alpha = animationProgress.value
        val scale = lerp(0.85f, 1f, animationProgress.value)
        scaleX = scale
        scaleY = scale
        translationY = lerp(30f, 0f, animationProgress.value)
    }
}
