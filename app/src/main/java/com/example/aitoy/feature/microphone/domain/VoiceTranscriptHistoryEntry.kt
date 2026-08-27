package com.example.aitoy.feature.microphone.domain

data class VoiceTranscriptHistoryEntry(
    val id: String,
    val text: String,
    val createdAtEpochMillis: Long
)
