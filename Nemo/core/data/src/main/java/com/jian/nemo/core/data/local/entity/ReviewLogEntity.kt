package com.jian.nemo.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jian.nemo.core.domain.model.ReviewLog

@Entity(
    tableName = "review_logs",
    indices = [
        Index(value = ["review_date"]) // 加速按时间查询
    ]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0, // 0 triggers auto-increment

    @ColumnInfo(name = "item_id")
    val itemId: Int,

    @ColumnInfo(name = "item_type")
    val itemType: String,

    @ColumnInfo(name = "review_date")
    val reviewDate: Long,

    @ColumnInfo(name = "interval_days")
    val intervalDays: Int,

    @ColumnInfo(name = "rating")
    val rating: Int
)

// Mapper Extension
fun ReviewLog.toEntity() = ReviewLogEntity(
    id = id,
    itemId = itemId,
    itemType = itemType,
    reviewDate = reviewDate,
    intervalDays = intervalDays,
    rating = rating
)

fun ReviewLogEntity.toDomain() = ReviewLog(
    id = id,
    itemId = itemId,
    itemType = itemType,
    reviewDate = reviewDate,
    intervalDays = intervalDays,
    rating = rating
)
