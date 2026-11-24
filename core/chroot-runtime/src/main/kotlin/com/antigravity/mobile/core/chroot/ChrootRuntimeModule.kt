package com.antigravity.mobile.core.chroot

import android.content.Context
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.RootExecutor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChrootIoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChrootHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChrootStagingDir

@Module
@InstallIn(SingletonComponent::class)
abstract class ChrootRuntimeBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAssetStreamProvider(impl: AndroidAssetStreamProvider): AssetStreamProvider

    @Binds
    @Singleton
    abstract fun bindRootfsDownloader(impl: KtorRootfsDownloader): RootfsDownloader

    @Binds
    @Singleton
    abstract fun bindRootfsInstaller(impl: DefaultRootfsInstaller): RootfsInstaller

    @Binds
    @Singleton
    abstract fun bindBindMountManager(impl: DefaultBindMountManager): BindMountManager

    @Binds
    @Singleton
    abstract fun bindChrootManager(impl: DefaultChrootManager): ChrootManager
}

@Module
@InstallIn(SingletonComponent::class)
object ChrootRuntimeProvidesModule {
    @Provides
    @ChrootIoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ChrootHttpClient
    fun provideChrootHttpClient(): HttpClient = HttpClient(CIO)

    @Provides
    @Singleton
    @ChrootStagingDir
    fun provideChrootStagingDir(@ApplicationContext context: Context): File =
        File(context.cacheDir, "chroot_staging").apply { mkdirs() }

    @Provides
    @Singleton
    fun provideDefaultBindMountManager(
        rootExecutor: RootExecutor,
        @ChrootIoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DefaultBindMountManager = DefaultBindMountManager(
        rootExecutor = rootExecutor,
        ioDispatcher = ioDispatcher,
    )

    @Provides
    @Singleton
    fun provideDefaultChrootManager(
        rootExecutor: RootExecutor,
        rootfsInstaller: RootfsInstaller,
        bindMountManager: BindMountManager,
        @ChrootIoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DefaultChrootManager = DefaultChrootManager(
        rootExecutor = rootExecutor,
        rootfsInstaller = rootfsInstaller,
        bindMountManager = bindMountManager,
        ioDispatcher = ioDispatcher,
    )
}
