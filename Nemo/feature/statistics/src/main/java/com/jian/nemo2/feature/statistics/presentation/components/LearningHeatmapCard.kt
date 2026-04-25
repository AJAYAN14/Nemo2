package com.jian.nemo2.feature.statistics.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.unit.dp
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import com.jian.nemo2.core.common.util.DateTimeUtils
import com.jian.nemo2.core.domain.usecase.statistics.HeatmapDay

// Heatmap Colors (GitHub Style)
// Heatmap Colors (Fire Style)
private val Level0 = Color(0xFFEBEDF0) // Empty
private val Level1 = Color(0xFFFFD7D5) // Low (Pale Red)
private val Level2 = Color(0xFFFFA39E) // Medium (Light Red)
private val Level3 = Color(0xFFFF4D4F) // High (Bright Red)
private val Level4 = Color(0xFFCF1322) // Intense (Deep Red)

// Dark Mode Colors (Fire Style)
private val Level0Dark = Color(0xFF161B22)
private val Level1Dark = Color(0xFF3A1C1C) // Dark Pale Red
private val Level2Dark = Color(0xFF682424) // Dark Medium Red
private val Level3Dark = Color(0xFFB52A2A) // Dark Bright Red
private val Level4Dark = Color(0xFFE63E3E) // Dark Intense Red

@Composable
fun LearningHeatmapCard(
    heatmapData: List<HeatmapDay>,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
    cardColor: Color = MaterialTheme.colorScheme.surface
) {
    if (heatmapData.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp) // Reduced padding
        ) {
            // Header removed (Redundant with Screen Title)

            Spacer(modifier = Modifier.height(16.dp))

            // Heatmap Content
            HeatmapContent(
                data = heatmapData,
                isDarkTheme = isDarkTheme
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            HeatmapLegend(isDarkTheme = isDarkTheme)
        }
    }
}

@Composable
private fun HeatmapContent(
    data: List<HeatmapDay>,
    isDarkTheme: Boolean
) {
    // Haptics
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Config
    val blockSize = 14.dp
    val spacing = 4.dp
    val blockSizePx = with(density) { blockSize.toPx() }
    val spacingPx = with(density) { spacing.toPx() }

    // Logic: 7 rows
    val totalDays = data.size
    val weeks = (totalDays + 6) / 7

    val totalWidth = (blockSize + spacing) * weeks
    val totalHeight = (blockSize + spacing) * 7

    // Auto scroll to end on first load
    LaunchedEffect(Unit) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    // Selected Info
    var selectedDay by remember { mutableStateOf<HeatmapDay?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        Canvas(
            modifier = Modifier
                .size(width = totalWidth, height = totalHeight)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            val col = (offset.x / (blockSizePx + spacingPx)).toInt()
                            val row = (offset.y / (blockSizePx + spacingPx)).toInt()
                            val index = col * 7 + row
                            if (index in data.indices) {
                                selectedDay = data[index]
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            }
                            tryAwaitRelease()
                            selectedDay = null
                        }
                    )
                }
        ) {
            data.forEachIndexed { index, day ->
                val col = index / 7
                val row = index % 7

                val x = col * (blockSizePx + spacingPx)
                val y = row * (blockSizePx + spacingPx)

                val color = getHeatmapColor(day.level, isDarkTheme)

                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(blockSizePx, blockSizePx),
                    cornerRadius = CornerRadius(with(density) { 2.dp.toPx() })
                )
            }
        }
    }

    // Selection Popup (Smart Tooltip with Smooth Layout Animation)
    AnimatedVisibility(
        visible = selectedDay != null,
        enter = expandVertically() + fadeIn() + scaleIn(),
        exit = shrinkVertically() + fadeOut() + scaleOut(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
         Box(contentAlignment = Alignment.Center) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (selectedDay != null) "${formatDate(selectedDay!!.date)}: ${selectedDay!!.count} 次学习" else "",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun HeatmapLegend(isDarkTheme: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "少",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp)
        )

        (0..4).forEach { level ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(10.dp)
                    .background(
                        color = getHeatmapColor(level, isDarkTheme),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }

        Text(
            text = "多",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

private fun getHeatmapColor(level: Int, isDark: Boolean): Color {
    return if (isDark) {
        when (level) {
            0 -> Level0Dark
            1 -> Level1Dark
            2 -> Level2Dark
            3 -> Level3Dark
            else -> Level4Dark
        }
    } else {
        when (level) {
            0 -> Level0
            1 -> Level1
            2 -> Level2
            3 -> Level3
            else -> Level4
        }
    }
}

private fun formatDate(epochDay: Long): String {
    return DateTimeUtils.formatEpochDayToDisplay(epochDay)
}
