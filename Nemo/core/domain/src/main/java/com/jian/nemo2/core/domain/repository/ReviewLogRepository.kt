package com.jian.nemo2.core.domain.repository

import com.jian.nemo2.core.domain.model.ReviewLog

interface ReviewLogRepository {
    suspend fun insertLog(log: ReviewLog)
    suspend fun getRecentLogs(limit: Int = 1500): List<ReviewLog>
}
