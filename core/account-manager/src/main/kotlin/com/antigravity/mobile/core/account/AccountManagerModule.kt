package com.antigravity.mobile.core.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.antigravity.mobile.contracts.AccountRepository
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.RootExecutor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AccountIoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AccountPrefsDataStore

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountManagerBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAccountStore(impl: RoomAccountStore): AccountStore

    @Binds
    @Singleton
    abstract fun bindOAuthBrowserLauncher(impl: AndroidChromeCustomTabsLauncher): OAuthBrowserLauncher

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: DefaultAccountRepository): AccountRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AccountManagerProvidesModule {
    @Provides
    @AccountIoDispatcher
    fun provideAccountIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideAccountDatabase(@ApplicationContext context: Context): AccountDatabase =
        Room.databaseBuilder(
            context,
            AccountDatabase::class.java,
            "antigravity_accounts.db",
        ).build()

    @Provides
    fun provideAccountProfileDao(database: AccountDatabase): AccountProfileDao =
        database.accountProfileDao()

    @Provides
    @Singleton
    @AccountPrefsDataStore
    fun provideAccountPrefsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("account_manager_prefs")
        }

    @Provides
    @Singleton
    fun provideProfileDirectoryIsolator(
        rootExecutor: RootExecutor,
        @AccountIoDispatcher ioDispatcher: CoroutineDispatcher,
    ): ProfileDirectoryIsolator = DefaultProfileDirectoryIsolator(
        rootExecutor = rootExecutor,
        ioDispatcher = ioDispatcher,
    )

    @Provides
    @Singleton
    fun provideAgyAuthBridge(
        chrootManager: ChrootManager,
        rootExecutor: RootExecutor,
        @AccountIoDispatcher ioDispatcher: CoroutineDispatcher,
    ): AgyAuthBridge = ChrootAgyAuthBridge(
        chrootManager = chrootManager,
        rootExecutor = rootExecutor,
        ioDispatcher = ioDispatcher,
    )

    @Provides
    @Singleton
    fun provideDefaultAccountRepository(
        store: AccountStore,
        isolator: ProfileDirectoryIsolator,
        authBridge: AgyAuthBridge,
        browserLauncher: OAuthBrowserLauncher,
        @AccountIoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DefaultAccountRepository = DefaultAccountRepository(
        store = store,
        isolator = isolator,
        authBridge = authBridge,
        browserLauncher = browserLauncher,
        ioDispatcher = ioDispatcher,
    )
}
