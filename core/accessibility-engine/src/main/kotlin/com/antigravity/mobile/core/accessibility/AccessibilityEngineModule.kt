package com.antigravity.mobile.core.accessibility

import com.antigravity.mobile.contracts.UiTreeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessibilityEngineModule {

    @Binds
    @Singleton
    abstract fun bindUiTreeProvider(impl: AccessibilityUiTreeProvider): UiTreeProvider

    companion object {
        @Provides
        @Singleton
        fun provideAccessibilityServiceBridge(): AccessibilityServiceBridge =
            AccessibilityServiceBridge.instance

        @Provides
        @Singleton
        fun provideNotificationBridge(): NotificationBridge =
            NotificationServiceBridge.instance
    }
}
