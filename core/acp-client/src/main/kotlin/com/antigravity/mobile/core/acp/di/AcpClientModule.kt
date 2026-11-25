package com.antigravity.mobile.core.acp.di

import com.antigravity.mobile.contracts.AcpClient
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.ContractsJson
import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.core.acp.AcpLaunchCommandBuilder
import com.antigravity.mobile.core.acp.AcpProcessLauncher
import com.antigravity.mobile.core.acp.DefaultAcpClient
import com.antigravity.mobile.core.acp.ProcessBuilderAcpProcessLauncher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object AcpClientModule {

    @Provides
    @Singleton
    fun provideAcpProcessLauncher(): AcpProcessLauncher =
        ProcessBuilderAcpProcessLauncher(ioDispatcher = Dispatchers.IO)

    @Provides
    @Singleton
    fun provideAcpLaunchCommandBuilder(
        chrootManager: ChrootManager,
        rootExecutor: RootExecutor,
    ): AcpLaunchCommandBuilder = AcpLaunchCommandBuilder(
        chrootManager = chrootManager,
        rootExecutor = rootExecutor,
    )

    @Provides
    @Singleton
    fun provideAcpClient(
        commandBuilder: AcpLaunchCommandBuilder,
        processLauncher: AcpProcessLauncher,
        chrootManager: ChrootManager,
    ): AcpClient = DefaultAcpClient(
        commandBuilder = commandBuilder,
        processLauncher = processLauncher,
        ioDispatcher = Dispatchers.IO,
        json = ContractsJson,
        chrootManager = chrootManager,
    )
}
