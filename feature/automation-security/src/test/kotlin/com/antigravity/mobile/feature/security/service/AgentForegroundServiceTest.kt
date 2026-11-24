package com.antigravity.mobile.feature.security.service

import android.content.pm.ServiceInfo
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentForegroundServiceTest {

    @Test
    fun `computeForegroundServiceType enforces specialUse and optional microphone safely on API 34+`() {
        // Android 14 (API 34) without microphone
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            AgentForegroundService.computeForegroundServiceType(
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                includeMicrophone = false,
                hasRecordAudioPermission = false,
            ),
        )

        // Android 15 (API 35) with microphone requested AND granted
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            AgentForegroundService.computeForegroundServiceType(
                sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM,
                includeMicrophone = true,
                hasRecordAudioPermission = true,
            ),
        )

        // Android 17 (API 37) with microphone requested WITHOUT RECORD_AUDIO permission -> omits microphone to prevent SecurityException
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            AgentForegroundService.computeForegroundServiceType(
                sdkInt = 37,
                includeMicrophone = true,
                hasRecordAudioPermission = false,
            ),
        )

        // Android 11 (API 30) with microphone granted
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            AgentForegroundService.computeForegroundServiceType(
                sdkInt = Build.VERSION_CODES.R,
                includeMicrophone = true,
                hasRecordAudioPermission = true,
            ),
        )

        // Android 10 (API 29)
        assertEquals(
            0,
            AgentForegroundService.computeForegroundServiceType(
                sdkInt = Build.VERSION_CODES.Q,
                includeMicrophone = true,
                hasRecordAudioPermission = true,
            ),
        )
    }
}
