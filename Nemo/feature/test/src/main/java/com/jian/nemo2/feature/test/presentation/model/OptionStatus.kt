package com.jian.nemo2.feature.test.presentation.model

/**
 * 选项状态枚举
 * 用于控制TestOption组件的显示样式
 */
enum class OptionStatus {
    DEFAULT,    // 默认状态
    SELECTED,   // 已选中
    CORRECT,    // 正确答案（提交后显示）
    INCORRECT   // 错误答案（提交后显示）
}
