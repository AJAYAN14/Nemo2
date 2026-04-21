package com.jian.nemo.feature.collection.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

/**
 * 通用收藏/错题操作菜单组件 - UI/UX Pro Max
 *
 * Design Spec:
 * - Menu Background: 
 *   - Light: Pure White (#FFFFFF) + High Elevation Shadow
 *   - Dark: Surface Container (#1C1C1E) + Subtle Border
 * - Shape: Rounded 16dp
 * - Typography: Inter/System Default (Clean)
 * - Icons: Rounded Material Icons
 * - Dialog: Premium Alert Dialog Style
 */
@Composable
fun CollectionActionMenu(
    wordCount: Int,
    grammarCount: Int,
    titleSuffix: String,
    onClearAll: () -> Unit,
    onClearWords: () -> Unit,
    onClearGrammars: () -> Unit
) {
    if (wordCount <= 0 && grammarCount <= 0) return

    var showMenu by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showClearWordsDialog by remember { mutableStateOf(false) }
    var showClearGrammarsDialog by remember { mutableStateOf(false) }

    // Premium Colors
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val menuBackgroundColor = if (isDark) MaterialTheme.colorScheme.surfaceContainer else Color.White
    val menuBorderColor = if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f) else Color.Transparent
    val menuShadowElevation = if (isDark) 4.dp else 12.dp
    
    val destructiveColor = MaterialTheme.colorScheme.error

    Box {
        PremiumIconButton(
            onClick = { showMenu = true },
            icon = Icons.Default.MoreVert,
            contentDescription = "更多选项"
        )

        MaterialTheme(
            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
        ) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = DpOffset(0.dp, 8.dp),
                containerColor = menuBackgroundColor,
                modifier = Modifier
                    .background(menuBackgroundColor, RoundedCornerShape(16.dp))
                    .border(1.dp, menuBorderColor, RoundedCornerShape(16.dp))
            ) {
                // Header (Optional, for structure)
                // Divider or Spacer might be good here if needed

                PremiumDropdownMenuItem(
                    text = "清除所有${titleSuffix}",
                    icon = Icons.Rounded.DeleteSweep,
                    color = destructiveColor,
                    onClick = {
                        showMenu = false
                        showClearAllDialog = true
                    }
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                )

                PremiumDropdownMenuItem(
                    text = "清除单词${titleSuffix}",
                    icon = Icons.Rounded.Delete,
                    color = MaterialTheme.colorScheme.onSurface,
                    enabled = wordCount > 0,
                    onClick = {
                        showMenu = false
                        showClearWordsDialog = true
                    }
                )

                PremiumDropdownMenuItem(
                    text = "清除语法${titleSuffix}",
                    icon = Icons.Rounded.FolderDelete,
                    color = MaterialTheme.colorScheme.onSurface,
                    enabled = grammarCount > 0,
                    onClick = {
                        showMenu = false
                        showClearGrammarsDialog = true
                    }
                )
            }
        }
    }

    // Dialogs
    if (showClearAllDialog) {
        PremiumAlertDialog(
            title = "清除所有${titleSuffix}",
            text = "确定要清除所有${titleSuffix}吗？此操作无法撤销，所有记录将永久删除。",
            icon = Icons.Rounded.Warning,
            confirmText = "清除全部",
            dismissText = "取消",
            onConfirm = {
                onClearAll()
                showClearAllDialog = false
            },
            onDismiss = { showClearAllDialog = false }
        )
    }

    if (showClearWordsDialog) {
        PremiumAlertDialog(
            title = "清除单词${titleSuffix}",
            text = "确定要清除所有单词${titleSuffix}吗？此操作无法撤销。",
            icon = Icons.Rounded.Delete,
            confirmText = "清除",
            dismissText = "取消",
            onConfirm = {
                onClearWords()
                showClearWordsDialog = false
            },
            onDismiss = { showClearWordsDialog = false }
        )
    }

    if (showClearGrammarsDialog) {
        PremiumAlertDialog(
            title = "清除语法${titleSuffix}",
            text = "确定要清除所有语法${titleSuffix}吗？此操作无法撤销。",
            icon = Icons.Rounded.FolderDelete,
            confirmText = "清除",
            dismissText = "取消",
            onConfirm = {
                onClearGrammars()
                showClearGrammarsDialog = false
            },
            onDismiss = { showClearGrammarsDialog = false }
        )
    }
}

@Composable
private fun PremiumIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        label = "iconScale",
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier.scale(scale)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface 
        )
    }
}

@Composable
private fun PremiumDropdownMenuItem(
    text: String,
    icon: ImageVector,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { 
            Text(
                text = text, 
                fontWeight = FontWeight.Medium,
                color = if(enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ) 
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if(enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        },
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun PremiumAlertDialog(
    title: String,
    text: String,
    icon: ImageVector,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val containerColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        containerColor = containerColor,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = androidx.compose.foundation.shape.CircleShape,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Text(dismissText, fontWeight = FontWeight.Medium)
            }
        }
    )
}
