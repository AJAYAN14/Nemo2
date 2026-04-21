package com.jian.nemo.feature.learning.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface CategoryLearningPreferences {
    fun getLastIndex(categoryId: String): Int
    fun saveLastIndex(categoryId: String, index: Int)
}

@Singleton
class CategoryLearningPreferencesImpl @Inject constructor(
    @ApplicationContext context: Context
) : CategoryLearningPreferences {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    override fun getLastIndex(categoryId: String): Int {
        return prefs.getInt(getKey(categoryId), 0)
    }

    override fun saveLastIndex(categoryId: String, index: Int) {
        prefs.edit().putInt(getKey(categoryId), index).apply()
    }

    private fun getKey(categoryId: String): String {
        return "last_index_$categoryId"
    }

    companion object {
        private const val PREF_NAME = "category_learning_prefs"
    }
}
