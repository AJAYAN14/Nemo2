package com.jian.nemo2.feature.learning.presentation.review

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 复习主界面（纯UI预览）
 *
 * @param onNavigateBack 返回回调
 * @param modifier 修饰符
 */
@Composable
fun ReviewScreen(
    onNavigateBack: () -> Unit,
    onStartReview: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        SessionPrepScreen(
            onBack = onNavigateBack,
            onStartReview = onStartReview
        )
    }
}
