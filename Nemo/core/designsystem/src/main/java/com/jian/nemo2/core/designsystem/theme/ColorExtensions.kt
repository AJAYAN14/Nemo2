package com.jian.nemo2.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ColorScheme扩展属性
 *
 * 为学习、复习、测试等页面提供与旧项目一致的专用色彩
 * 参考: _reference/old-nemo/app/src/main/java/com/jian/nemo/ui/screen/LearningScreen.kt:65-67
 */

/**
 * 页面语义背景色
 */
val ColorScheme.surfaceBackground: Color
    @Composable
    get() = if (isSystemInDarkTheme()) {
        NemoSurfaceBackgroundDark
    } else {
        NemoSurfaceBackground
    }

/**
 * 页面语义卡片容器色
 */
val ColorScheme.surfaceCard: Color
    @Composable
    get() = if (isSystemInDarkTheme()) {
        NemoSurfaceCardDark
    } else {
        NemoSurfaceCard
    }

// 废弃带有特定页面名称的扩展，建议使用通用的 surfaceBackground / surfaceCard
@Deprecated("Use surfaceBackground", ReplaceWith("surfaceBackground"))
val ColorScheme.learningBackground: Color @Composable get() = surfaceBackground

@Deprecated("Use surfaceCard", ReplaceWith("surfaceCard"))
val ColorScheme.learningCardBackground: Color @Composable get() = surfaceCard

@Deprecated("Use surfaceBackground", ReplaceWith("surfaceBackground"))
val ColorScheme.testBackground: Color @Composable get() = surfaceBackground

@Deprecated("Use surfaceCard", ReplaceWith("surfaceCard"))
val ColorScheme.testCardBackground: Color @Composable get() = surfaceCard
