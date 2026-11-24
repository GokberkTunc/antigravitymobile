package com.antigravity.mobile.di

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.antigravity.mobile.core.accessibility.AntigravityAccessibilityService
import com.antigravity.mobile.core.accessibility.AntigravityNotificationListenerService
import com.antigravity.mobile.runtime.AppRuntimeLogger
import com.antigravity.mobile.system.AntigravityDeviceAdminReceiver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppIoDispatcher

/**
 * Resolved Android component identifiers for privileged system services declared in `:app`.
 */
data class AppComponentMetadata(
    val packageName: String,
    val accessibilityServiceComponent: String,
    val notificationListenerComponent: String,
    val deviceAdminComponent: ComponentName,
)

@Module
@InstallIn(SingletonComponent::class)
object AppBindingsModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @AppIoDispatcher ioDispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    @Provides
    @Singleton
    @AppIoDispatcher
    fun provideAppIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideDevicePolicyManager(
        @ApplicationContext context: Context,
    ): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    @Provides
    @Singleton
    fun provideAppComponentMetadata(
        @ApplicationContext context: Context,
    ): AppComponentMetadata {
        val pkg = context.packageName
        return AppComponentMetadata(
            packageName = pkg,
            accessibilityServiceComponent = "$pkg/${AntigravityAccessibilityService::class.java.name}",
            notificationListenerComponent = "$pkg/${AntigravityNotificationListenerService::class.java.name}",
            deviceAdminComponent = AntigravityDeviceAdminReceiver.componentName(context),
        )
    }

    @Provides
    @Singleton
    fun provideAppRuntimeLogger(): AppRuntimeLogger = object : AppRuntimeLogger {
        override fun info(tag: String, message: String) {
            Log.i(tag, message)
        }

        override fun warn(tag: String, message: String, throwable: Throwable?) {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        }
    }
}
