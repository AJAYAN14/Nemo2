package com.jian.nemo.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import coil.compose.AsyncImage

/**
 * 可复用的头像组件
 * 支持图片头像和基于用户名的文字头像
 *
 * @param username 用户名，用于生成文字头像
 * @param avatarPath 头像图片路径，如果为空则显示文字头像
 * @param size 头像大小
 * @param borderWidth 边框宽度，默认为0
 * @param borderColor 边框颜色
 * @param padding 头像与边框之间的内边距
 * @param useGradientBorder 是否使用渐变色边框（Google One 风格）
 * @param shape 头像形状，默认为圆形
 * @param modifier 修饰符
 */
@Composable
fun AvatarImage(
    username: String,
    avatarPath: String? = null,
    size: Dp = 48.dp,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    padding: Dp = 0.dp,
    useGradientBorder: Boolean = false,
    shape: Shape = CircleShape,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }

    // 生成基于用户名的颜色
    val backgroundColor = remember(username) {
        generateColorFromUsername(username)
    }

    // 获取首字母
    val initial = remember(username) {
        username.takeIf { it.isNotEmpty() }?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    // 解析预设头像
    val presetAvatar = remember(avatarPath) {
        // 使用完整路径作为 key (avatarPath)
        com.jian.nemo.core.ui.util.PresetAvatars.getPresetId(avatarPath)?.let {
             com.jian.nemo.core.ui.util.PresetAvatars.getById(it)
        }
    }

    val isImageAvatar = !avatarPath.isNullOrEmpty() && !com.jian.nemo.core.ui.util.PresetAvatars.isPreset(avatarPath)

    // 计算渐变边框的绘制参数 - Apple 风格渐变
    val gradientColors = remember {
        listOf(
            Color(0xFF6366F1), // 深沉靛蓝 (Indigo)
            Color(0xFF8B5CF6), // 优雅紫色 (Purple)
            Color(0xFFEC4899), // 玫瑰粉 (Pink)
            Color(0xFFF59E0B), // 温暖琥珀 (Amber)
            Color(0xFF10B981), // 翡翠绿 (Emerald)
            Color(0xFF6366F1)  // 回到靛蓝形成循环
        )
    }

    Box(
        modifier = modifier
            .size(size)
            // ... (drawBehind logic for custom border)
            .then(
                if (useGradientBorder && borderWidth > 0.dp) {
                    // 使用自定义渐变边框
                    Modifier.drawBehind {
                        val borderWidthPx = borderWidth.toPx()
                        val radius = size.toPx() / 2f
                        val center = Offset(radius, radius)

                        // 使用 sweepGradient 绘制圆形渐变边框
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = gradientColors,
                                center = center
                            ),
                            radius = radius - borderWidthPx / 2f,
                            style = Stroke(width = borderWidthPx)
                        )
                    }
                } else if (borderWidth > 0.dp) {
                    // 使用普通边框
                     Modifier.border(borderWidth, borderColor, shape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // 内层头像可用空间
        val safeSize: Dp = size - (borderWidth * 2) - (padding * 2)
        
        // 内层元素基准尺寸计算
        val innerSize = safeSize * 0.4f
        
        // 内层头像容器，添加内边距
        Box(
            modifier = Modifier
                .size(width = safeSize, height = safeSize)
                .clip(shape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(width = innerSize, height = innerSize),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                }
                presetAvatar != null -> {
                    // ✅ 预设头像：本地即时渲染
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                            .drawBehind {
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = presetAvatar.colors,
                                        start = Offset(0f, 0f),
                                        end = Offset(this.size.width, this.size.height)
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                         presetAvatar.emoji?.let { emoji ->
                             Text(
                                 text = emoji,
                                 fontSize = (innerSize.value).sp,
                                 textAlign = TextAlign.Center
                             )
                         }
                    }
                }
                isImageAvatar -> {
                    // ✅ 改进：使用 Coil 支持网络和缓存图片加载，并添加详细状态处理
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(avatarPath)
                        .crossfade(true)
                        // 强制指定一个稳定的 Key（忽略 URL 参数变化），比如只用纯数字 userId 或去除参数后的基础 URL
                        .diskCacheKey(avatarPath?.substringBefore("?"))
                        .memoryCacheKey(avatarPath?.substringBefore("?"))
                        // 强制使用磁盘缓存
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()

                    var isError by remember { mutableStateOf(false) }

                    if (isError) {
                        // 回退显示文字头像
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                fontSize = (innerSize.value).sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics {
                                    this.contentDescription = "用户 $username 的默认头像，显示首字母 $initial (图片加载失败)"
                                }
                            )
                        }
                    } else {
                        AsyncImage(
                            model = request,
                            contentDescription = "用户 $username 的个人头像图片",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(shape),
                            contentScale = ContentScale.Crop,
                            onState = { state ->
                                if (state is coil.compose.AsyncImagePainter.State.Error) {
                                    isError = true
                                }
                            }
                        )
                    }
                }
                else -> {
                    // ✅ 改进：显示文字头像（增强无障碍支持）
                    // 使用 Box 包裹 Text 以确保完全居中（解决字体基线对齐问题）
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            fontSize = (innerSize.value).sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics {
                                this.contentDescription = "用户 $username 的默认头像，显示首字母 $initial"
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 根据用户名生成固定的颜色
 * 使用哈希算法确保同一用户名总是生成相同的颜色
 */
private fun generateColorFromUsername(username: String): Color {
    val colors = listOf(
        Color(0xFFE57373), // Red
        Color(0xFFF06292), // Pink
        Color(0xFFBA68C8), // Purple
        Color(0xFF9575CD), // Deep Purple
        Color(0xFF7986CB), // Indigo
        Color(0xFF5C6BC0), // Indigo
        Color(0xFF42A5F5), // Blue
        Color(0xFF29B6F6), // Light Blue
        Color(0xFF26C6DA), // Cyan
        Color(0xFF26A69A), // Teal
        Color(0xFF66BB6A), // Green
        Color(0xFF8BC34A), // Light Green
        Color(0xFFDCE775), // Lime
        Color(0xFFFFEE58), // Yellow
        Color(0xFFFFCA28), // Amber
        Color(0xFFFFB74D), // Orange
        Color(0xFFFF8A65), // Deep Orange
        Color(0xFFA1887F), // Brown
        Color(0xFF90A4AE), // Blue Grey
        Color(0xFF78909C)  // Blue Grey
    )

    // 使用用户名的哈希值来选择颜色
    val hash = username.hashCode()
    val index = kotlin.math.abs(hash) % colors.size
    return colors[index]
}
