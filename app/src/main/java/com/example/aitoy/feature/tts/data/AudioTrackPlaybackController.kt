package com.example.aitoy.feature.tts.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicLong

class AudioTrackPlaybackController {
    private var audioTrack: AudioTrack? = null
    private val playbackToken = AtomicLong(0L)

    fun play(
        samples: FloatArray,
        sampleRate: Int,
        onComplete: () -> Unit
    ) {
        stop()

        if (samples.isEmpty()) {
            onComplete()
            return
        }

        val token = playbackToken.incrementAndGet()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * java.lang.Float.BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        track.notificationMarkerPosition = samples.size
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack?) {
                    if (playbackToken.get() != token) {
                        return
                    }
                    stop()
                    onComplete()
                }

                override fun onPeriodicNotification(track: AudioTrack?) = Unit
            }
        )

        audioTrack = track
        track.play()
    }

    fun stop() {
        playbackToken.incrementAndGet()
        audioTrack?.runCatching {
            pause()
            flush()
            stop()
            release()
        }
        audioTrack = null
    }

    fun release() {
        stop()
    }
}
