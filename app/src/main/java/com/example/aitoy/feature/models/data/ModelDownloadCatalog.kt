package com.example.aitoy.feature.models.data

object ModelDownloadCatalog {
    val asrGigaAmV3Rnnt = RemoteModelSpec(
        id = "asr_gigaam_v3_rnnt",
        displayName = "ASR GigaAM v3 RNNT",
        repoId = "Smirnov75/GigaAM-v3-sherpa-onnx",
        revision = "6888903da215c7735f51101d939f3bfa679fb2b8",
        files = listOf(
            RemoteModelFile(
                remotePath = "gigaam_v3_rnnt_tokens.txt",
                targetRelativePath = "models/asr/gigaam_v3_rnnt/tokens.txt",
                expectedSizeBytes = 195L
            ),
            RemoteModelFile(
                remotePath = "gigaam_v3_rnnt_encoder.onnx",
                targetRelativePath = "models/asr/gigaam_v3_rnnt/encoder.onnx",
                expectedSizeBytes = 885_084_896L
            ),
            RemoteModelFile(
                remotePath = "gigaam_v3_rnnt_decoder.onnx",
                targetRelativePath = "models/asr/gigaam_v3_rnnt/decoder.onnx",
                expectedSizeBytes = 3_331_577L
            ),
            RemoteModelFile(
                remotePath = "gigaam_v3_rnnt_joint.onnx",
                targetRelativePath = "models/asr/gigaam_v3_rnnt/joiner.onnx",
                expectedSizeBytes = 1_440_448L
            )
        )
    )

    val llmGemma4E2BIt = RemoteModelSpec(
        id = "llm_gemma_4_e2b_it",
        displayName = "LLM Gemma 4 E2B it",
        repoId = "litert-community/gemma-4-E2B-it-litert-lm",
        revision = "753814ee8334c2b737c24ed45da9d4b354ab6f01",
        files = listOf(
            RemoteModelFile(
                remotePath = "gemma-4-E2B-it.litertlm",
                targetRelativePath = "models/llm/gemma-4-e2b-it/gemma-4-E2B-it.litertlm",
                expectedSizeBytes = 2_583_085_056L
            )
        )
    )

    val vadSilero = RemoteModelSpec(
        id = "vad_silero",
        displayName = "VAD Silero sherpa-onnx",
        repoId = "k2-fsa/sherpa-onnx",
        revision = "asr-models",
        files = listOf(
            RemoteModelFile(
                remotePath = "silero_vad.onnx",
                targetRelativePath = "models/vad/silero_vad.onnx",
                expectedSizeBytes = 643_854L,
                directUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
            )
        )
    )

    val ttsPiperRussianVoicePack = RemoteModelSpec(
        id = "tts_piper_russian_voice_pack",
        displayName = "TTS Piper Russian voice pack",
        repoId = "k2-fsa/sherpa-onnx",
        revision = "tts-models",
        files = listOf(
            RemoteModelFile(
                remotePath = "vits-piper-ru_RU-dmitri-medium.tar.bz2",
                targetRelativePath = "models/tts/piper-voices/vits-piper-ru_RU-dmitri-medium.tar.bz2",
                expectedSizeBytes = 67_188_551L,
                directUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-dmitri-medium.tar.bz2",
                archiveFormat = RemoteArchiveFormat.TarBz2
            ),
            RemoteModelFile(
                remotePath = "vits-piper-ru_RU-irina-medium.tar.bz2",
                targetRelativePath = "models/tts/piper-voices/vits-piper-ru_RU-irina-medium.tar.bz2",
                expectedSizeBytes = 67_153_308L,
                directUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-irina-medium.tar.bz2",
                archiveFormat = RemoteArchiveFormat.TarBz2
            )
        )
    )
}

data class RemoteModelSpec(
    val id: String,
    val displayName: String,
    val repoId: String,
    val revision: String,
    val files: List<RemoteModelFile>
)

data class RemoteModelFile(
    val remotePath: String,
    val targetRelativePath: String,
    val expectedSizeBytes: Long,
    val directUrl: String? = null,
    val archiveFormat: RemoteArchiveFormat = RemoteArchiveFormat.None
)

enum class RemoteArchiveFormat {
    None,
    TarBz2
}
