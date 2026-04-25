package com.jian.nemo2.feature.learning.di

import com.jian.nemo2.feature.learning.data.preferences.CategoryLearningPreferences
import com.jian.nemo2.feature.learning.data.preferences.CategoryLearningPreferencesImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LearningModule {

    @Binds
    @Singleton
    abstract fun bindCategoryLearningPreferences(
        impl: CategoryLearningPreferencesImpl
    ): CategoryLearningPreferences
}
