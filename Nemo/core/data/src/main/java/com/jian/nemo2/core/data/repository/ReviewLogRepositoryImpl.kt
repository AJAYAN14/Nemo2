package com.jian.nemo2.core.data.repository

import com.jian.nemo2.core.data.local.dao.ReviewLogDao
import com.jian.nemo2.core.data.local.entity.toDomain
import com.jian.nemo2.core.data.local.entity.toEntity
import com.jian.nemo2.core.domain.model.ReviewLog
import com.jian.nemo2.core.domain.repository.ReviewLogRepository
import javax.inject.Inject

class ReviewLogRepositoryImpl @Inject constructor(
    private val reviewLogDao: ReviewLogDao
) : ReviewLogRepository {

    override suspend fun insertLog(log: ReviewLog) {
        reviewLogDao.insertLog(log.toEntity())
    }

    override suspend fun getRecentLogs(limit: Int): List<ReviewLog> {
        return reviewLogDao.getRecentLogs(limit).map { it.toDomain() }
    }
}
