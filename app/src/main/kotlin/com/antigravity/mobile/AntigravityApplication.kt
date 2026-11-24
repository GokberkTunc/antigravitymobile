package com.antigravity.mobile

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.antigravity.mobile.runtime.AppRuntimeSupervisor
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Application entry point configuring:
 * - `libsu` with `Shell.FLAG_MOUNT_MASTER` (KernelSU/Magisk/APatch compatible) and debug-only verbose logging
 * - Hilt + WorkManager (`Configuration.Provider` using [HiltWorkerFactory])
 * - Off-Main-Thread startup of [AppRuntimeSupervisor] (`KillSwitchCoordinator`, loopback MCP server,
 *   Accessibility/ACP health observation)
 */
@HiltAndroidApp
class AntigravityApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var runtimeSupervisor: AppRuntimeSupervisor

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()

    override fun onCreate() {
        configureLibsuShell()
        super.onCreate()
        if (::runtimeSupervisor.isInitialized) {
            runtimeSupervisor.start()
        }
    }

    companion object {
        private const val LIBSU_TIMEOUT_SECONDS = 20L
        private val shellConfigured = AtomicBoolean(false)

        init {
            configureLibsuShell()
        }

        fun configureLibsuShell() {
            if (!shellConfigured.compareAndSet(false, true)) return
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            try {
                Shell.setDefaultBuilder(
                    Shell.Builder.create()
                        .setFlags(Shell.FLAG_MOUNT_MASTER)
                        .setTimeout(LIBSU_TIMEOUT_SECONDS),
                )
            } catch (_: IllegalStateException) {
                // Default builder already initialized in this process.
            }
        }
    }
}
