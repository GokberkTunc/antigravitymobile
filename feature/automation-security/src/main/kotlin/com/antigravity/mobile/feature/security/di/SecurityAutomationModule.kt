package com.antigravity.mobile.feature.security.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.antigravity.mobile.contracts.AcpClient
import com.antigravity.mobile.contracts.AutomationScheduler
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.OverlayController
import com.antigravity.mobile.contracts.SecurityPolicyEngine
import com.antigravity.mobile.contracts.UiTreeProvider
import com.antigravity.mobile.feature.security.automation.AcpScheduledTaskExecutor
import com.antigravity.mobile.feature.security.automation.AndroidScheduledTaskNotifier
import com.antigravity.mobile.feature.security.automation.AndroidTaskDispatchBackend
import com.antigravity.mobile.feature.security.automation.AndroidWakeLockRunner
import com.antigravity.mobile.feature.security.automation.AutomationSchedulerImpl
import com.antigravity.mobile.feature.security.automation.AutomationStore
import com.antigravity.mobile.feature.security.automation.DataStoreAutomationStore
import com.antigravity.mobile.feature.security.automation.ScheduledTaskExecutor
import com.antigravity.mobile.feature.security.automation.ScheduledTaskNotifier
import com.antigravity.mobile.feature.security.automation.TaskDispatchBackend
import com.antigravity.mobile.feature.security.automation.WakeLockRunner
import com.antigravity.mobile.feature.security.killswitch.AndroidKillSwitchNotifier
import com.antigravity.mobile.feature.security.killswitch.KillSwitchCoordinator
import com.antigravity.mobile.feature.security.killswitch.KillSwitchNotifier
import com.antigravity.mobile.feature.security.mcp.McpConfigGenerator
import com.antigravity.mobile.feature.security.policy.DataStoreSecurityPolicyStore
import com.antigravity.mobile.feature.security.policy.DefaultSecurityPolicyEngine
import com.antigravity.mobile.feature.security.policy.SecurityPolicyStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SecurityPolicyDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AutomationDataStore

@Module
@InstallIn(SingletonComponent::class)
object SecurityAutomationModule {

    @Provides
    @Singleton
    @SecurityPolicyDataStore
    fun provideSecurityPolicyDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("antigravity_security_policy") },
    )

    @Provides
    @Singleton
    @AutomationDataStore
    fun provideAutomationDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("antigravity_automation_scheduler") },
    )

    @Provides
    @Singleton
    fun provideSecurityPolicyStore(
        @SecurityPolicyDataStore dataStore: DataStore<Preferences>,
    ): SecurityPolicyStore = DataStoreSecurityPolicyStore(dataStore, Dispatchers.IO)

    @Provides
    @Singleton
    fun provideKillSwitchNotifier(
        @ApplicationContext context: Context,
    ): KillSwitchNotifier = AndroidKillSwitchNotifier(context)

    @Provides
    @Singleton
    fun provideKillSwitchCoordinator(
        uiTreeProvider: UiTreeProvider,
        acpClient: AcpClient,
        chrootManager: ChrootManager,
        overlayController: OverlayController,
        notifier: KillSwitchNotifier,
    ): KillSwitchCoordinator = KillSwitchCoordinator(
        uiTreeProvider = uiTreeProvider,
        acpClient = acpClient,
        chrootManager = chrootManager,
        overlayController = overlayController,
        notifier = notifier,
        ioDispatcher = Dispatchers.IO,
    )

    @Provides
    @Singleton
    fun provideDefaultSecurityPolicyEngine(
        store: SecurityPolicyStore,
        killSwitchCoordinator: KillSwitchCoordinator,
    ): DefaultSecurityPolicyEngine = DefaultSecurityPolicyEngine(
        store = store,
        killSwitchCoordinator = killSwitchCoordinator,
    )

    @Provides
    @Singleton
    fun provideSecurityPolicyEngine(
        impl: DefaultSecurityPolicyEngine,
    ): SecurityPolicyEngine = impl

    @Provides
    @Singleton
    fun provideMcpConfigGenerator(): McpConfigGenerator = McpConfigGenerator(Dispatchers.IO)

    @Provides
    @Singleton
    fun provideAutomationStore(
        @AutomationDataStore dataStore: DataStore<Preferences>,
    ): AutomationStore = DataStoreAutomationStore(dataStore, Dispatchers.IO)

    @Provides
    @Singleton
    fun provideWakeLockRunner(
        @ApplicationContext context: Context,
    ): WakeLockRunner = AndroidWakeLockRunner(context)

    @Provides
    @Singleton
    fun provideTaskDispatchBackend(
        @ApplicationContext context: Context,
    ): TaskDispatchBackend = AndroidTaskDispatchBackend(context)

    @Provides
    @Singleton
    fun provideScheduledTaskExecutor(
        acpClient: AcpClient,
    ): ScheduledTaskExecutor = AcpScheduledTaskExecutor(acpClient, Dispatchers.IO)

    @Provides
    @Singleton
    fun provideScheduledTaskNotifier(
        @ApplicationContext context: Context,
    ): ScheduledTaskNotifier = AndroidScheduledTaskNotifier(context)

    @Provides
    @Singleton
    fun provideAutomationSchedulerImpl(
        store: AutomationStore,
        dispatchBackend: TaskDispatchBackend,
        wakeLockRunner: WakeLockRunner,
        taskExecutor: ScheduledTaskExecutor,
        notifier: ScheduledTaskNotifier,
    ): AutomationSchedulerImpl = AutomationSchedulerImpl(
        store = store,
        dispatchBackend = dispatchBackend,
        wakeLockRunner = wakeLockRunner,
        taskExecutor = taskExecutor,
        notifier = notifier,
        ioDispatcher = Dispatchers.IO,
    )

    @Provides
    @Singleton
    fun provideAutomationScheduler(
        impl: AutomationSchedulerImpl,
    ): AutomationScheduler = impl
}
