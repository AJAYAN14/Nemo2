package com.jian.nemo2.feature.statistics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jian.nemo2.core.designsystem.theme.*
import com.jian.nemo2.core.ui.component.common.CommonHeader
import com.jian.nemo2.feature.statistics.presentation.components.LearningHeatmapCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 学习热力图与数据高光专属界面 (Activity Heatmap Pro Max)
 */
@Composable
fun ActivityHeatmapScreen(
    onBack: () -> Unit,
    viewModel: ActivityHeatmapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val backgroundColor = MaterialTheme.colorScheme.background

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    Scaffold(
        topBar = {
            CommonHeader(
                title = "学习热力图", // Use dedicated title
                onBack = onBack
            )
        },
        containerColor = backgroundColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // 1. Heatmap (Top Centerpiece)
            item {
                AnimatedVisibility(
                    visible = isVisible
                ) {
                    Column {
                        HeatmapSectionTitle("年度回顾")
                        if (uiState.isLoading) {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            LearningHeatmapCard(
                                heatmapData = uiState.heatmapData,
                                isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                                cardColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) MaterialTheme.colorScheme.surfaceContainer else Color.White
                            )
                        }
                    }
                }
            }

            // 2. Rich Stats
            if (!uiState.isLoading) {
                item {
                    AnimatedVisibility(
                        visible = isVisible
                    ) {
                        Column {
                            HeatmapSectionTitle("数据高光")
                            RichStatsGrid(
                                streak = uiState.streak,
                                longestStreak = uiState.longestStreak,
                                totalActiveDays = uiState.totalActiveDays,
                                bestDayCount = uiState.bestDayCount,
                                bestDayDate = uiState.bestDayDate,
                                dailyAverage = uiState.dailyAverage,
                                todayCount = uiState.todayCount
                            )
                        }
                    }
                }

                // 3. Motivational Footer
                item {
                   AnimatedVisibility(
                        visible = isVisible
                   ) {
                       Box(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                           Text(
                               text = "每一天都在进步，保持连胜！",
                               style = MaterialTheme.typography.bodySmall,
                               color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                           )
                       }
                   }
                }
            }
        }
    }
}

// Re-using the RichStatsGrid logic here (copied from previous iteration)
@Composable
private fun RichStatsGrid(
    streak: Int,
    longestStreak: Int,
    totalActiveDays: Int,
    bestDayCount: Int,
    bestDayDate: Long,
    dailyAverage: Int,
    todayCount: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Streak
            RichStatItem(
                label = "当前坚持",
                value = "$streak 天",
                subLabel = "最长 $longestStreak 天",
                icon = Icons.Rounded.EmojiEvents, // Trophy icon
                color = NemoOrange,
                modifier = Modifier.weight(1f)
            )

            // Total Days
            RichStatItem(
                label = "累计活跃",
                value = "$totalActiveDays 天",
                subLabel = "持续进步",
                icon = Icons.Rounded.History, // History icon
                color = NemoPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Best Day
            val dateStr = if (bestDayDate > 0) {
                 val formatter = SimpleDateFormat("MM/dd", Locale.CHINA)
                 formatter.format(Date(bestDayDate * 86_400_000L))
            } else "--/--"

            RichStatItem(
                label = "单日最佳",
                value = "$bestDayCount 项",
                subLabel = dateStr,
                icon = Icons.Rounded.Create,
                color = NemoSecondary,
                modifier = Modifier.weight(1f)
            )

             // Encouragement -> Daily Average
             RichStatItem(
                label = "日均学习",
                value = "$dailyAverage 词",
                subLabel = if (todayCount >= dailyAverage && dailyAverage > 0) "状态极佳" else "保持节奏",
                icon = Icons.Rounded.Book,
                color = NemoIndigo,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RichStatItem(
    label: String,
    value: String,
    subLabel: String,
    icon: Any,
    color: Color,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val containerColor = if (isDark) MaterialTheme.colorScheme.surfaceContainer else Color.White
    val borderColor = if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    val shadowElevation = if (isDark) 2.dp else 10.dp
    val shadowColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.04f)

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = containerColor,
        border = BorderStroke(0.5.dp, borderColor),
        shadowElevation = shadowElevation,
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(26.dp),
                spotColor = shadowColor,
                ambientColor = shadowColor
            )
    ) {
         Column(
             horizontalAlignment = Alignment.Start,
             modifier = Modifier.padding(20.dp)
         ) {
             Text(
                 text = label,
                 style = MaterialTheme.typography.labelMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant
             )
             Spacer(modifier = Modifier.height(8.dp))
             Text(
                 text = value,
                 style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                 color = color
             )
             Spacer(modifier = Modifier.height(4.dp))
             Text(
                 text = subLabel,
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
             )
         }
    }
}

@Composable
private fun HeatmapSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}
