package com.jian.nemo.core.ui.util

import androidx.compose.ui.graphics.Color

/**
 * 预设头像管理器
 * 提供一组预设的渐变色头像供用户选择
 */
object PresetAvatars {

    /**
     * 预设头像数据类
     * @param id 唯一标识
     * @param name 头像名称
     * @param colors 渐变色列表
     * @param emoji 代表emoji（可选）
     */
    data class PresetAvatar(
        val id: String,
        val name: String,
        val colors: List<Color>,
        val emoji: String? = null
    )

    /**
     * 预设头像列表
     */
    val presets = listOf(
        // 经典系列
        PresetAvatar(
            id = "sunset",
            name = "日落",
            colors = listOf(
                Color(0xFFFF6B6B),  // 红
                Color(0xFFFFAA00),  // 橙
                Color(0xFFFFD93D)   // 黄
            ),
            emoji = "🌅"
        ),
        PresetAvatar(
            id = "ocean",
            name = "海洋",
            colors = listOf(
                Color(0xFF06BEE1),  // 青
                Color(0xFF0B4F6C),  // 深蓝
                Color(0xFF01BAEF)   // 浅蓝
            ),
            emoji = "🌊"
        ),
        PresetAvatar(
            id = "forest",
            name = "森林",
            colors = listOf(
                Color(0xFF06D6A0),  // 绿
                Color(0xFF118AB2),  // 蓝绿
                Color(0xFF073B4C)   // 深绿
            ),
            emoji = "🌲"
        ),
        PresetAvatar(
            id = "lavender",
            name = "薰衣草",
            colors = listOf(
                Color(0xFF9D4EDD),  // 紫
                Color(0xFF7209B7),  // 深紫
                Color(0xFFC77DFF)   // 浅紫
            ),
            emoji = "💜"
        ),

        // 现代系列
        PresetAvatar(
            id = "mint",
            name = "薄荷",
            colors = listOf(
                Color(0xFF06FFA5),  // 薄荷绿
                Color(0xFF00D9FF),  // 青
                Color(0xFF00B4D8)   // 蓝
            ),
            emoji = "🍃"
        ),
        PresetAvatar(
            id = "coral",
            name = "珊瑚",
            colors = listOf(
                Color(0xFFFF6B9D),  // 粉红
                Color(0xFFC9184A),  // 深粉
                Color(0xFFFF8FA3)   // 浅粉
            ),
            emoji = "🪸"
        ),
        PresetAvatar(
            id = "golden",
            name = "金色",
            colors = listOf(
                Color(0xFFFFBE0B),  // 金黄
                Color(0xFFFB5607),  // 橙红
                Color(0xFFFF006E)   // 品红
            ),
            emoji = "✨"
        ),
        PresetAvatar(
            id = "aurora",
            name = "极光",
            colors = listOf(
                Color(0xFF7209B7),  // 紫
                Color(0xFF3A0CA3),  // 蓝紫
                Color(0xFF4361EE)   // 蓝
            ),
            emoji = "🌌"
        ),

        // 温暖系列
        PresetAvatar(
            id = "peach",
            name = "蜜桃",
            colors = listOf(
                Color(0xFFFFB5A7),  // 蜜桃色
                Color(0xFFFCD5CE),  // 浅粉
                Color(0xFFF8EDEB)   // 米白
            ),
            emoji = "🍑"
        ),
        PresetAvatar(
            id = "autumn",
            name = "秋叶",
            colors = listOf(
                Color(0xFFD00000),  // 红
                Color(0xFFDC2F02),  // 橙红
                Color(0xFFE85D04)   // 橙
            ),
            emoji = "🍂"
        ),

        // 清新系列
        PresetAvatar(
            id = "sky",
            name = "天空",
            colors = listOf(
                Color(0xFF48CAE4),  // 天蓝
                Color(0xFF00B4D8),  // 深天蓝
                Color(0xFF90E0EF)   // 浅天蓝
            ),
            emoji = "☁️"
        ),
        PresetAvatar(
            id = "lemon",
            name = "柠檬",
            colors = listOf(
                Color(0xFFFFFF3F),  // 柠檬黄
                Color(0xFFFFEE32),  // 黄
                Color(0xFFFDD835)   // 深黄
            ),
            emoji = "🍋"
        ),

        // 神秘系列
        PresetAvatar(
            id = "midnight",
            name = "午夜",
            colors = listOf(
                Color(0xFF03045E),  // 深蓝
                Color(0xFF023E8A),  // 蓝
                Color(0xFF0077B6)   // 浅蓝
            ),
            emoji = "🌙"
        ),
        PresetAvatar(
            id = "galaxy",
            name = "星系",
            colors = listOf(
                Color(0xFF240046),  // 深紫
                Color(0xFF3C096C),  // 紫
                Color(0xFF5A189A)   // 浅紫
            ),
            emoji = "🌟"
        ),

        // 活力系列
        PresetAvatar(
            id = "tropical",
            name = "热带",
            colors = listOf(
                Color(0xFFFF006E),  // 品红
                Color(0xFFFB5607),  // 橙
                Color(0xFFFFBE0B)   // 黄
            ),
            emoji = "🏖️"
        ),
        PresetAvatar(
            id = "cherry",
            name = "樱花",
            colors = listOf(
                Color(0xFFFFB3C6),  // 樱花粉
                Color(0xFFFF8FAB),  // 粉
                Color(0xFFFFC2D1)   // 浅粉
            ),
            emoji = "🌸"
        )
    )

    /**
     * 根据ID获取预设头像
     */
    fun getById(id: String): PresetAvatar? {
        return presets.find { it.id == id }
    }

    /**
     * 获取随机预设头像
     */
    fun getRandom(): PresetAvatar {
        return presets.random()
    }

    /**
     * 根据用户名获取推荐的预设头像
     * （基于用户名哈希，确保同一用户总是相同的推荐）
     */
    fun getRecommendedForUser(username: String): PresetAvatar {
        val hash = username.hashCode()
        val index = kotlin.math.abs(hash) % presets.size
        return presets[index]
    }

    // ========================================================================
    // 协议定义
    // ========================================================================

    const val PRESET_PREFIX = "preset:"

    /**
     * 检查路径是否为预设头像协议
     */
    fun isPreset(path: String?): Boolean = path?.startsWith(PRESET_PREFIX) == true

    /**
     * 从协议路径中解析预设ID
     */
    fun getPresetId(path: String?): String? {
        if (!isPreset(path)) return null
        return path?.removePrefix(PRESET_PREFIX)
    }

    /**
     * 生成预设头像协议路径
     */
    fun createPresetPath(id: String): String = "$PRESET_PREFIX$id"
}
