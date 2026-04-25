package com.jian.nemo2.feature.test.presentation.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

/**
 * 测试设置对话框组件
 *
 * 提取自：TestSettingsScreen.kt 行139-235
 */

/**
 * 自定义题目数量对话框
 *
 * @param show 是否显示
 * @param initialValue 初始值
 * @param onDismiss 关闭回调
 * @param onConfirm 确认回调，参数为用户输入的数量
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomQuestionCountDialog(
    show: Boolean,
    initialValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    if (!show) return

    // 内部状态
    var currentValue by remember(show) { mutableIntStateOf(initialValue) }

    // 预设选项
    val presets = remember { listOf(10, 15, 20, 25, 30, 50) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        val isDark = isSystemInDarkTheme()
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
            tonalElevation = 0.dp, // Remove tonal/gradient overlay
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                // 1. 标题
                Text(
                    text = "自定义题数",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "设置本次测试的题目数量",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )

                // 2. 核心交互区域 ( -  数值  + )
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 减号按钮
                    FilledIconButton(
                        onClick = { if (currentValue > 1) currentValue-- },
                        enabled = currentValue > 1,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text("-", style = MaterialTheme.typography.titleLarge)
                    }

                    // 数值显示
                    Text(
                        text = "$currentValue",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .widthIn(min = 64.dp), // 防止数字变动导致抖动
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    // 加号按钮
                    FilledIconButton(
                        onClick = { if (currentValue < 1000) currentValue++ },
                        enabled = currentValue < 1000,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }

                Text(
                    text = "题",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // 3. 快速预设 (Chips)
                FlowRow(
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = currentValue == preset,
                            onClick = { currentValue = preset },
                            label = { Text("${preset}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = currentValue == preset,
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 4. 底部操作按钮
                Row(
                   modifier = Modifier.fillMaxWidth()
                ) {
                    // 取消按钮
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("取消", style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 确认按钮
                    Button(
                        onClick = {
                            if (currentValue in 1..1000) {
                                onConfirm(currentValue)
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp), // Fully rounded pill shape
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Text("确定", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 自定义时间限制对话框 - UI/UX Pro Max
 *
 * @param show 是否显示
 * @param initialValue 初始值（分钟）
 * @param onDismiss 关闭回调
 * @param onConfirm 确认回调，参数为用户输入的分钟数
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomTimeLimitDialog(
    show: Boolean,
    initialValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    if (!show) return

    // 内部状态
    var currentValue by remember(show) { mutableIntStateOf(initialValue) }

    // 预设选项
    val presets = remember { listOf(5, 10, 20, 30, 45, 60) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        val isDark = isSystemInDarkTheme()
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
            tonalElevation = 0.dp, // Remove tonal/gradient overlay
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                // 1. 标题
                Text(
                    text = "自定义时长",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "设置测试时间限制 (分钟)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )

                // 2. 核心交互区域 ( -  数值  + )
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 减号按钮
                    FilledIconButton(
                        onClick = { if (currentValue > 0) currentValue-- },
                        enabled = currentValue > 0,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text("-", style = MaterialTheme.typography.titleLarge)
                    }

                    // 数值显示
                    Text(
                        text = "$currentValue",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .widthIn(min = 64.dp), // 防止数字变动导致抖动
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    // 加号按钮
                    FilledIconButton(
                        onClick = { if (currentValue < 180) currentValue++ },
                        enabled = currentValue < 180,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }

                Text(
                    text = if (currentValue == 0) "不限时间" else "分钟",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // 3. 快速预设 (Chips)
                FlowRow(
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = currentValue == preset,
                            onClick = { currentValue = preset },
                            label = { Text("${preset}m") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = currentValue == preset,
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 4. 底部操作按钮
                Row(
                   modifier = Modifier.fillMaxWidth()
                ) {
                    // 取消按钮
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("取消", style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 确认按钮
                    Button(
                        onClick = {
                            if (currentValue in 0..180) {
                                onConfirm(currentValue)
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp), // Fully rounded pill shape
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Text("确定", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
    }
}
