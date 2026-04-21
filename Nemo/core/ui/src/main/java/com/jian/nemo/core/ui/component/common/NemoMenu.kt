package com.jian.nemo.core.ui.component.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Nemo 风格的顶级下拉菜单 (Premium Design)
 *
 * 遵循项目 UI/UX 规范：
 * - 圆角: 16dp (一致性)
 * - 阴影: 12dp (Glassmorphism 质感模拟)
 * - 边框: 极细的 0.5dp 边框增强层级
 * - 配色: 使用 NemoPrimary 与 Surface 语义色
 *
 * @param expanded 是否展开
 * @param onDismissRequest 关闭回调
 * @param modifier 修饰符
 * @param offset 偏移量
 * @param content 菜单内容 (使用 NemoMenuItem)
 */
@Composable
fun NemoDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 8.dp), // 增加垂直偏移，避免紧贴按钮
    content: @Composable ColumnScope.() -> Unit
) {
    // 容器颜色：使用主题定义的 Surface 颜色 (通常是 NemoSurfaceCard)
    val containerColor = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(16.dp)

    MaterialTheme(
        shapes = MaterialTheme.shapes.copy(extraSmall = shape)
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier
                .background(containerColor, shape)
                .border(
                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    shape
                )
                .widthIn(min = 200.dp), // 增加最小宽度，显得更大气
            offset = offset,
            containerColor = containerColor,
            tonalElevation = 0.dp, // 禁用默认 tonal，使用 shadow
            shadowElevation = 12.dp, // 增强阴影，营造悬浮感
            content = content
        )
    }
}

/**
 * Nemo 风格的菜单项 (Premium Design)
 *
 * - 字体: Title Medium (16sp) 提升可读性
 * - 图标: 使用主题色 (NemoPrimary) 增强视觉引导
 * - 间距: 宽敞的 Horizontal Padding
 *
 * @param text 文本
 * @param onClick 点击回调
 * @param leadingIcon 图标 (可选)
 * @param trailingIcon 尾部图标 (可选)
 * @param enabled 是否启用
 * @param isDestructive 是否是破坏性操作 (红色)
 */
@Composable
fun NemoMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isDestructive: Boolean = false
) {
    // 使用主题色 (Primary) 或 错误色 (Error)
    val mainColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val iconTint = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary // 普通图标使用主色调，更精致
    }

    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium, // 使用稍大的字体 (16sp)
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp, // 微调
                color = mainColor
            )
        },
        onClick = onClick,
        modifier = modifier,
        leadingIcon = if (leadingIcon != null) {
            {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint.copy(alpha = if (enabled) 1f else 0.38f)
                )
            }
        } else null,
        trailingIcon = if (trailingIcon != null) {
            {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconTint.copy(alpha = if (enabled) 0.7f else 0.38f)
                )
            }
        } else null,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = mainColor,
            leadingIconColor = iconTint,
            trailingIconColor = iconTint,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp) // 增加间距
    )
}
