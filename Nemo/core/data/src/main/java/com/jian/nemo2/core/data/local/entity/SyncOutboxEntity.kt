package com.jian.nemo2.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 离线任务队列实体
 * 用于存储待同步到 Supabase 的复习操作
 */
@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "item_id")
    val itemId: Long,

    @ColumnInfo(name = "item_type")
    val itemType: String,

    /** 用户评分 (1:Again, 2:Hard, 3:Good, 4:Easy)，非 REVIEW 操作可设为 0 */
    val rating: Int,

    /** 操作类型: REVIEW, SUSPEND, UNSUSPEND, BURY, FAVORITE */
    @ColumnInfo(name = "action_type")
    val actionType: String = "REVIEW",

    /** 附加数据 (JSON)，如 buryItem 的 epochDay 或 favorite 的布尔值 */
    val payload: String? = null,

    /** 操作发生的时间戳 (ISO String) */
    @ColumnInfo(name = "created_at")
    val createdAt: String,

    /** 用户操作时的预期 last_review 时间戳 (用于解决 STALE_DATA_CONFLICT) */
    @ColumnInfo(name = "expected_last_review")
    val expectedLastReview: String? = null,

    /** 是否正在同步中 */
    @ColumnInfo(name = "is_syncing")
    val isSyncing: Boolean = false,

    /** 失败重试次数 */
    val attempts: Int = 0
)
