package com.jian.nemo.core.ui.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * "推箱子"转场动画 - iOS 风格 3D 推入版
 *
 * 效果：
 * - 前进：B 从右侧推入（略微放大 1.02 → 1.0），A 被推出（略微缩小 1.0 → 0.98）
 * - 返回：A 从左侧推入（从 0.98 恢复到 1.0），B 被推出（放大到 1.02）
 *
 * 特点：
 * ✅ Z 轴分层：scale 动画营造空间感和深度
 * ✅ 严格对称：enter/exit 位移距离完全一致
 * ✅ 无残留：旧页面完全移出屏幕，不会有重影/层级问题
 * ✅ iOS 风格：类似 NavigationView push 的高级感
 * ✅ 流畅淡入：150ms 符合 Material 规范 (90-150ms)
 *
 * 本质：
 * 整体画布移动 + Z 轴深度变化，新页面"从前方推入"，旧页面"向后方退出"
 */
object NemoNavigationAnimations {

    /**
     * 滑动动画时长，M3 规范推荐 300ms
     */
    private const val ANIMATION_DURATION = 300

    /**
     * 淡入淡出时长，Material 规范推荐 150ms
     * fade in: 90-150ms，fade out: 120-180ms
     */
    private const val FADE_DURATION = 150

    /**
     * 进入页面初始缩放（略微放大，营造"从前方推入"的感觉）
     */
    private const val ENTER_INITIAL_SCALE = 1.02f

    /**
     * 退出页面目标缩放（略微缩小，营造"向后方退出"的感觉）
     */
    private const val EXIT_TARGET_SCALE = 0.98f

    /**
     * 进入动画 (前进时，新页面的动画)
     * B 页面从右侧屏幕外完全滑入 + 淡入 + 从略微放大恢复到正常大小
     * 营造"从前景推入"的 3D 感
     */
    @OptIn(ExperimentalAnimationApi::class)
    fun enterTransition(): EnterTransition {
        return slideInHorizontally(
            initialOffsetX = { it }, // 从右侧屏幕外开始（完整宽度）
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = FADE_DURATION
            )
        ) + scaleIn(
            initialScale = ENTER_INITIAL_SCALE, // 1.02 → 1.0，营造推入感
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        )
    }

    /**
     * 退出动画 (前进时，旧页面的动画)
     * A 页面被完全推出到左侧屏幕外 + 略微缩小
     * 营造"向后景退出"的 3D 感，避免内容残留和层级混乱
     */
    @OptIn(ExperimentalAnimationApi::class)
    fun exitTransition(): ExitTransition {
        return slideOutHorizontally(
            targetOffsetX = { -it }, // 完全滑出到左侧屏幕外（与enter严格对称）
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        ) + scaleOut(
            targetScale = EXIT_TARGET_SCALE, // 1.0 → 0.98，营造退后感
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        )
    }

    /**
     * 返回进入动画 (返回时，前一个页面的动画)
     * A 页面从左侧屏幕外完全滑入 + 淡入 + 从缩小状态恢复到正常大小
     * 呼应前进时的退出效果（与exit严格镜像）
     */
    @OptIn(ExperimentalAnimationApi::class)
    fun popEnterTransition(): EnterTransition {
        return slideInHorizontally(
            initialOffsetX = { -it }, // 从左侧屏幕外开始
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = FADE_DURATION
            )
        ) + scaleIn(
            initialScale = EXIT_TARGET_SCALE, // 0.98 → 1.0，从后景恢复
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        )
    }

    /**
     * 返回退出动画 (返回时，当前页面的动画)
     * B 页面完全滑出到右侧屏幕外 + 淡出 + 略微放大
     * 营造"向前景推出"的感觉（与enter严格镜像）
     */
    @OptIn(ExperimentalAnimationApi::class)
    fun popExitTransition(): ExitTransition {
        return slideOutHorizontally(
            targetOffsetX = { it }, // 完全滑出到右侧屏幕外
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = FADE_DURATION
            )
        ) + scaleOut(
            targetScale = ENTER_INITIAL_SCALE, // 1.0 → 1.02，向前推出
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        )
    }
}
