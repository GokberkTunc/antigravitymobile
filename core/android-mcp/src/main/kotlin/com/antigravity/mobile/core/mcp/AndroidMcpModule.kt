package com.antigravity.mobile.core.mcp

import com.antigravity.mobile.contracts.AndroidMcpServer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AndroidMcpModule {

    @Binds
    @Singleton
    abstract fun bindAndroidSystemToolGateway(
        impl: PlatformAndroidSystemToolGateway,
    ): AndroidSystemToolGateway

    @Binds
    @Singleton
    abstract fun bindAndroidMcpServer(
        impl: KtorAndroidMcpServer,
    ): AndroidMcpServer
}
