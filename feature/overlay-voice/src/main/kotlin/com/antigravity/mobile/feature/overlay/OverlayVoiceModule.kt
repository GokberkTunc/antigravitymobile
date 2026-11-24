package com.antigravity.mobile.feature.overlay

import com.antigravity.mobile.contracts.OverlayController
import com.antigravity.mobile.contracts.VoiceTranscriber
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OverlayVoiceModule {

    @Binds
    @Singleton
    abstract fun bindOverlayController(impl: OverlayControllerImpl): OverlayController

    @Binds
    @Singleton
    abstract fun bindVoiceTranscriber(impl: AndroidSpeechVoiceTranscriber): VoiceTranscriber
}
