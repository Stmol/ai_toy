package com.example.aitoy.feature.microphone.data

import android.content.Context
import androidx.core.content.edit
import com.example.aitoy.feature.microphone.domain.VoiceTranscriptHistoryEntry
import com.example.aitoy.feature.microphone.domain.VoiceTranscriptHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SharedPreferencesVoiceTranscriptHistoryRepository(
    context: Context
) : VoiceTranscriptHistoryRepository {
    private val sharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    private val _historyEntries = MutableStateFlow(loadEntries())
    override val historyEntries: StateFlow<List<VoiceTranscriptHistoryEntry>> =
        _historyEntries.asStateFlow()

    override suspend fun addTranscript(text: String) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) {
            return
        }

        withContext(Dispatchers.IO) {
            val updatedEntries = listOf(
                VoiceTranscriptHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    text = normalizedText,
                    createdAtEpochMillis = System.currentTimeMillis()
                )
            ) + _historyEntries.value

            persistEntries(updatedEntries)
            _historyEntries.value = updatedEntries
        }
    }

    override suspend fun deleteTranscript(id: String) {
        withContext(Dispatchers.IO) {
            val updatedEntries = _historyEntries.value.filterNot { it.id == id }
            if (updatedEntries.size == _historyEntries.value.size) {
                return@withContext
            }

            persistEntries(updatedEntries)
            _historyEntries.value = updatedEntries
        }
    }

    private fun loadEntries(): List<VoiceTranscriptHistoryEntry> {
        val rawValue = sharedPreferences.getString(KEY_HISTORY_ENTRIES, null)
            ?: return emptyList()

        return runCatching {
            JSONArray(rawValue).toEntryList()
        }.getOrDefault(emptyList())
            .sortedByDescending(VoiceTranscriptHistoryEntry::createdAtEpochMillis)
    }

    private fun persistEntries(entries: List<VoiceTranscriptHistoryEntry>) {
        val serializedEntries = JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject().apply {
                        put(KEY_ID, entry.id)
                        put(KEY_TEXT, entry.text)
                        put(KEY_CREATED_AT, entry.createdAtEpochMillis)
                    }
                )
            }
        }

        sharedPreferences.edit {
            putString(KEY_HISTORY_ENTRIES, serializedEntries.toString())
        }
    }

    private fun JSONArray.toEntryList(): List<VoiceTranscriptHistoryEntry> {
        return buildList(length()) {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val id = item.optString(KEY_ID).trim()
                val text = item.optString(KEY_TEXT).trim()
                if (id.isEmpty() || text.isEmpty()) {
                    continue
                }

                add(
                    VoiceTranscriptHistoryEntry(
                        id = id,
                        text = text,
                        createdAtEpochMillis = item.optLong(KEY_CREATED_AT, 0L)
                    )
                )
            }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "voice_transcript_history"
        const val KEY_HISTORY_ENTRIES = "history_entries"
        const val KEY_ID = "id"
        const val KEY_TEXT = "text"
        const val KEY_CREATED_AT = "created_at"
    }
}
