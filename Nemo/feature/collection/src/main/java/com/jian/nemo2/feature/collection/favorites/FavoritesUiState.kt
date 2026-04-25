package com.jian.nemo2.feature.collection.favorites

import com.jian.nemo2.core.domain.model.Word

/**
 * 收藏列表UI状态
 *
 * 参考：错误处理规范.md
 */
data class FavoritesUiState(
    val favoriteWords: List<Word> = emptyList(),
    val favoriteWordsCount: Int = 0,
    val favoriteGrammarsCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)
