package com.jian.nemo2.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 滑动点指示器组件
 * 用于HorizontalPager的页面指示
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlidingDotIndicator(
    pagerState: androidx.compose.foundation.pager.PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
    dotSize: Dp = 8.dp,
    spacing: Dp = 8.dp
) {
    // 计算当前页面（取模）
    val currentPage = pagerState.currentPage % pageCount
    // 获取页面偏移比例
    val offsetFraction = pagerState.currentPageOffsetFraction

    // 计算指示器应该偏移的X坐标比例
    val progress = (currentPage + offsetFraction) / (pageCount - 1)
    val clampedProgress = progress.coerceIn(0f, 1f)

    // 计算总宽度和点的大小
    val totalWidth = dotSize * pageCount + spacing * (pageCount - 1)
    val indicatorOffset = (totalWidth - dotSize) * clampedProgress

    Box(
        modifier = modifier
            .width(totalWidth)
            .height(dotSize),
        contentAlignment = Alignment.CenterStart
    ) {
        // 底部的"轨道"（灰色的点）
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            repeat(pageCount) {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .background(inactiveColor, CircleShape)
                )
            }
        }

        // 上层的"滑动点"（彩色的点）
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .size(dotSize)
                .background(activeColor, CircleShape)
        )
    }
}
