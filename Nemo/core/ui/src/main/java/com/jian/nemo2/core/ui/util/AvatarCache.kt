package com.jian.nemo2.core.ui.util

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 头像缓存管理器
 * 使用LruCache缓存已加载的头像，避免重复文件I/O
 */
object AvatarCache {

    private const val TAG = "AvatarCache"

    // 缓存大小：4MB（足够存储约50个512x512的头像）
    private const val CACHE_SIZE = 4 * 1024 * 1024 // 4MB

    // LruCache：键为文件路径，值为ImageBitmap
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_SIZE) {
        override fun sizeOf(key: String, bitmap: ImageBitmap): Int {
            // 计算Bitmap占用的内存大小（字节）
            return bitmap.width * bitmap.height * 4 // ARGB_8888 = 4 bytes per pixel
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: ImageBitmap,
            newValue: ImageBitmap?
        ) {
            if (evicted) {
                Log.d(TAG, "缓存条目被移除: $key (自动清理)")
            }
        }
    }

    /**
     * 从缓存获取头像
     *
     * @param path 头像文件路径
     * @return ImageBitmap或null
     */
    fun get(path: String): ImageBitmap? {
        val bitmap = cache.get(path)
        if (bitmap != null) {
            Log.d(TAG, "缓存命中: $path")
        }
        return bitmap
    }

    /**
     * 将头像存入缓存
     *
     * @param path 头像文件路径
     * @param bitmap ImageBitmap对象
     */
    fun put(path: String, bitmap: ImageBitmap) {
        cache.put(path, bitmap)
        Log.d(TAG, "已缓存: $path, 大小: ${bitmap.width}x${bitmap.height}, 缓存使用: ${cache.size() / 1024}KB / ${CACHE_SIZE / 1024}KB")
    }

    /**
     * 将Bitmap转换并存入缓存
     *
     * @param path 头像文件路径
     * @param bitmap Bitmap对象
     */
    fun put(path: String, bitmap: Bitmap) {
        try {
            val imageBitmap = bitmap.asImageBitmap()
            put(path, imageBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap转ImageBitmap失败: $path", e)
        }
    }

    /**
     * 移除指定路径的缓存
     *
     * @param path 头像文件路径
     */
    fun remove(path: String) {
        val removed = cache.remove(path)
        if (removed != null) {
            Log.d(TAG, "已移除缓存: $path")
        }
    }

    /**
     * 清空所有缓存
     */
    fun clear() {
        cache.evictAll()
        Log.d(TAG, "已清空所有缓存")
    }

    /**
     * 获取缓存统计信息
     *
     * @return Pair<命中次数, 未命中次数>
     */
    fun getStats(): Pair<Int, Int> {
        return Pair(cache.hitCount(), cache.missCount())
    }

    /**
     * 获取当前缓存大小（字节）
     *
     * @return 缓存占用的内存大小
     */
    fun getCacheSize(): Int {
        return cache.size()
    }

    /**
     * 获取缓存使用率
     *
     * @return 0.0 到 1.0 之间的值
     */
    fun getCacheUsage(): Float {
        return cache.size().toFloat() / CACHE_SIZE
    }

    /**
     * 打印缓存统计信息
     */
    fun printStats() {
        val (hits, misses) = getStats()
        val total = hits + misses
        val hitRate = if (total > 0) (hits.toFloat() / total * 100) else 0f

        Log.d(TAG, """
            ===== AvatarCache 统计 =====
            缓存大小: ${cache.size() / 1024}KB / ${CACHE_SIZE / 1024}KB (${String.format("%.1f", getCacheUsage() * 100)}%)
            缓存条目: ${cache.snapshot().size}
            命中次数: $hits
            未命中次数: $misses
            命中率: ${String.format("%.1f", hitRate)}%
            ============================
        """.trimIndent())
    }
}

