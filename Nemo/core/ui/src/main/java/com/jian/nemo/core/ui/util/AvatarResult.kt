package com.jian.nemo.core.ui.util

/**
 * 头像操作结果
 * 使用sealed class提供详细的错误信息
 */
sealed class AvatarResult {
    /**
     * 操作成功
     * @param path 头像文件路径
     * @param message 成功消息
     */
    data class Success(
        val path: String,
        val message: String = "头像已保存"
    ) : AvatarResult()

    /**
     * 操作失败
     */
    sealed class Error : AvatarResult() {
        abstract val message: String
        abstract val details: String?

        /**
         * 权限被拒绝
         */
        data class PermissionDenied(
            override val message: String = "权限被拒绝",
            override val details: String? = "需要存储或相机权限才能设置头像"
        ) : Error()

        /**
         * 文件不存在
         */
        data class FileNotFound(
            val path: String,
            override val message: String = "文件不存在",
            override val details: String? = "选择的图片文件不存在或已被删除"
        ) : Error()

        /**
         * 存储空间不足
         */
        data class StorageFull(
            val availableSpace: Long,
            val requiredSpace: Long,
            override val message: String = "存储空间不足",
            override val details: String? = "需要至少 ${requiredSpace / 1024 / 1024}MB 空间"
        ) : Error()

        /**
         * 无效的图片文件
         */
        data class InvalidImage(
            val reason: String,
            override val message: String = "无效的图片文件",
            override val details: String? = reason
        ) : Error()

        /**
         * 图片过大
         */
        data class ImageTooLarge(
            val actualSize: Long,
            val maxSize: Long,
            override val message: String = "图片文件过大",
            override val details: String? = "图片大小 ${actualSize / 1024 / 1024}MB 超过限制 ${maxSize / 1024 / 1024}MB"
        ) : Error()

        /**
         * 图片压缩失败
         */
        data class CompressionFailed(
            val exception: Exception,
            override val message: String = "图片处理失败",
            override val details: String? = exception.message
        ) : Error()

        /**
         * 文件保存失败
         */
        data class SaveFailed(
            val exception: Exception,
            override val message: String = "保存失败",
            override val details: String? = exception.message
        ) : Error()

        /**
         * 未知错误
         */
        data class Unknown(
            val exception: Exception,
            override val message: String = "操作失败",
            override val details: String? = exception.message
        ) : Error()
    }

    /**
     * 获取用户友好的错误消息
     */
    fun getUserMessage(): String {
        return when (this) {
            is Success -> message
            is Error -> "$message${if (details != null) "：$details" else ""}"
        }
    }

    /**
     * 判断是否成功
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * 判断是否失败
     */
    fun isError(): Boolean = this is Error

    /**
     * 获取路径（如果成功）
     */
    fun getPathOrNull(): String? {
        return when (this) {
            is Success -> path
            is Error -> null
        }
    }
}
