package com.jian.nemo2.core.data.di

import com.jian.nemo2.core.domain.service.SrsCalculator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 模块: 提供基于 FSRS 算法的 SRS 计算器实例
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SrsModule {

    /**
     * 绑定 SrsCalculator 接口到 FSRS 实现类
     */
    @Binds
    @Singleton
    abstract fun bindSrsCalculator(
        implementation: com.jian.nemo2.core.domain.algorithm.SrsCalculatorImpl
    ): SrsCalculator
}
