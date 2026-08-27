package com.example.aitoy.feature.microphone.domain

import kotlinx.coroutines.flow.StateFlow

interface VoiceTranscriptHistoryRepository {
    val historyEntries: StateFlow<List<VoiceTranscriptHistoryEntry>>

    suspend fun addTranscript(text: String)

    suspend fun deleteTranscript(id: String)
}
