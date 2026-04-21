package com.jian.nemo.core.data.local

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jian.nemo.core.data.util.DataSeedService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.jian.nemo.core.common.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据库创建回调
 * 首次创建数据库时自动导入数据
 */
@Singleton
class NemoDatabaseCallback @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val dataSeedService: DataSeedService
) : RoomDatabase.Callback() {

    companion object {
        private const val TAG = "NemoDatabaseCallback"
    }

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        Log.d(TAG, "🗄️ 数据库首次创建 (初始化由 onOpen 接管)")
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        Log.i(TAG, "🔵 数据库已打开，启动数据填充检查")

        applicationScope.launch {
            try {
                dataSeedService.ensureDataSeeded()
            } catch (e: Exception) {
                Log.e(TAG, "❌ 数据填充失败: ${e.message}", e)
            }
        }
    }
}

