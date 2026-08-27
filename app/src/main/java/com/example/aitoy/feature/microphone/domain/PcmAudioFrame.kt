package com.example.aitoy.feature.microphone.domain

data class PcmAudioFrame(
    val samples: ShortArray,
    val sampleRateHz: Int,
    val channelCount: Int
)
