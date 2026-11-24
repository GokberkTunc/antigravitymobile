package com.antigravity.mobile.core.root

import com.antigravity.mobile.contracts.BootstrapReport
import com.antigravity.mobile.contracts.RootEnvironmentInfo
import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.ShellResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [RootExecutor] powered by `libsu` (`Shell.FLAG_MOUNT_MASTER`), executing all
 * commands strictly off the Main Thread (`Dispatchers.IO`), honoring timeouts and cooperative
 * cancellation (`CancellationException` is never swallowed).
 */
@Singleton
class LibsuRootExecutor(
    private val shellBackend: RootShellBackend,
    private val environmentDetector: RootEnvironmentDetector,
    private val permissionBootstrapper: RootPermissionBootstrapper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RootExecutor {

    @Inject
    constructor(
        shellBackend: RootShellBackend,
        environmentDetector: RootEnvironmentDetector,
        permissionBootstrapper: RootPermissionBootstrapper,
    ) : this(
        shellBackend = shellBackend,
        environmentDetector = environmentDetector,
        permissionBootstrapper = permissionBootstrapper,
        ioDispatcher = Dispatchers.IO,
    )

    override suspend fun isRootAvailable(): Boolean = withContext(ioDispatcher) {
        try {
            shellBackend.isRootGranted()
        } catch (ce: CancellationException) {
            throw ce
        }
    }

    override suspend fun detectEnvironment(): RootEnvironmentInfo = withContext(ioDispatcher) {
        try {
            environmentDetector.detect()
        } catch (ce: CancellationException) {
            throw ce
        }
    }

    override suspend fun exec(
        command: String,
        timeoutMs: Long,
        cwd: String?,
    ): ShellResult = withContext(ioDispatcher) {
        require(command.isNotBlank()) { "command must not be blank" }
        require(timeoutMs > 0L) { "timeoutMs must be positive, got $timeoutMs" }
        val effectiveCommand = if (cwd.isNullOrBlank()) {
            command
        } else {
            "cd ${ShellEscaping.quote(cwd)} && $command"
        }
        try {
            shellBackend.executeCommand(effectiveCommand, timeoutMs)
        } catch (ce: CancellationException) {
            throw ce
        }
    }

    override suspend fun execArgv(
        argv: List<String>,
        timeoutMs: Long,
        cwd: String?,
    ): ShellResult = exec(
        command = ShellEscaping.joinArgv(argv),
        timeoutMs = timeoutMs,
        cwd = cwd,
    )

    override suspend fun bootstrapPermissions(
        packageName: String,
        accessibilityServiceComponent: String?,
        notificationListenerComponent: String?,
    ): BootstrapReport = withContext(ioDispatcher) {
        try {
            permissionBootstrapper.bootstrap(
                packageName = packageName,
                accessibilityServiceComponent = accessibilityServiceComponent,
                notificationListenerComponent = notificationListenerComponent,
            )
        } catch (ce: CancellationException) {
            throw ce
        }
    }
}
