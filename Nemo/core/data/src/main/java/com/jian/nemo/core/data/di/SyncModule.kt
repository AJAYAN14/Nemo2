package com.jian.nemo.core.data.di


import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    // BackupRepository has been removed in favor of SyncRepository

}
