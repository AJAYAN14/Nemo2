package com.jian.nemo2.core.data.remote

import com.jian.nemo2.core.domain.model.LearningSession

/**
 * 远程学习会话数据源
 */
interface SessionRemoteDataSource {

    /**
     * 获取指定类型和等级的远程会话
     */
    suspend fun getSession(itemType: String, level: String): LearningSession?

    /**
     * 保存或更新远程会话
     */
    suspend fun saveSession(session: LearningSession)

    /**
     * 更新当前进度
     */
    suspend fun updateCurrentIndex(itemType: String, level: String, currentIndex: Int)

    /**
     * 清除会话
     */
    suspend fun clearSession(itemType: String, level: String)
}
