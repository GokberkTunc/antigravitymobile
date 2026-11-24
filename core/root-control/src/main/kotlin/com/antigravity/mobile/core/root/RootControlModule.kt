package com.antigravity.mobile.core.root

import com.antigravity.mobile.contracts.RootExecutor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RootControlModule {

    @Binds
    @Singleton
    abstract fun bindRootShellBackend(impl: LibsuShellBackend): RootShellBackend

    @Binds
    @Singleton
    abstract fun bindRootExecutor(impl: LibsuRootExecutor): RootExecutor

    @Binds
    @Singleton
    abstract fun bindSuFileGateway(impl: LibsuSuFileGateway): SuFileGateway

    @Binds
    @Singleton
    abstract fun bindSuFileRepository(impl: LibsuFileRepository): SuFileRepository
}
