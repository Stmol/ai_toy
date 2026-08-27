package com.example.aitoy.feature.models.data

import android.content.Context
import android.os.StatFs
import android.os.SystemClock
import android.text.format.Formatter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

class OkHttpModelDownloadRepository(
    private val context: Context,
    private val httpClient: OkHttpClient
) : ModelDownloadRepository {
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val cancelFlags = ConcurrentHashMap<String, AtomicBoolean>()

    override fun downloadModel(spec: RemoteModelSpec): Flow<ModelDownloadEvent> = flow {
        val tempRoot = File(context.cacheDir, "model-downloads/${spec.id}")

        emit(ModelDownloadEvent.Idle)

        try {
            clearCancellation(spec.id)
            tempRoot.deleteRecursively()
            tempRoot.mkdirs()

            val totalBytes = resolveRemoteSize(spec)
            emit(ModelDownloadEvent.Preparing(totalBytes))
            ensureStorageAvailable(spec, totalBytes)

            var downloadedBytes = 0L
            val downloadedFiles = linkedMapOf<RemoteModelFile, File>()

            spec.files.forEach { remoteFile ->
                ensureNotCancelled(spec.id)
                val tempFile = File(tempRoot, remoteFile.targetRelativePath.substringAfterLast('/') + ".part")
                val fileSize = downloadFile(
                    spec = spec,
                    remoteFile = remoteFile,
                    destination = tempFile,
                    downloadedBytesBefore = downloadedBytes,
                    totalBytes = totalBytes
                ) { currentDownloadedBytes ->
                    emit(
                        ModelDownloadEvent.Downloading(
                            downloadedBytes = currentDownloadedBytes,
                            totalBytes = totalBytes,
                            progressPercent = calculateProgress(currentDownloadedBytes, totalBytes)
                        )
                    )
                }
                downloadedBytes += fileSize
                downloadedFiles[remoteFile] = tempFile
            }

            ensureNotCancelled(spec.id)
            emit(ModelDownloadEvent.Installing)
            installModel(spec, downloadedFiles)
            emit(ModelDownloadEvent.Completed)
        } catch (error: DownloadCancelledException) {
            tempRoot.deleteRecursively()
            emit(ModelDownloadEvent.Cancelled)
        } catch (error: Throwable) {
            tempRoot.deleteRecursively()
            if (cancellationFlag(spec.id).get()) {
                emit(ModelDownloadEvent.Cancelled)
            } else {
                emit(ModelDownloadEvent.Failed(error.toUserMessage(spec)))
            }
        } finally {
            unregisterActiveCall(spec.id)
            clearCancellation(spec.id)
            tempRoot.deleteRecursively()
        }
    }.flowOn(Dispatchers.IO)

    override fun cancelDownload(modelId: String) {
        cancellationFlag(modelId).set(true)
        activeCalls[modelId]?.cancel()
    }

    private fun resolveRemoteSize(spec: RemoteModelSpec): Long {
        return spec.files.sumOf { remoteFile -> remoteFile.expectedSizeBytes }
    }

    private fun ensureStorageAvailable(spec: RemoteModelSpec, totalBytes: Long) {
        val bufferBytes = ceil(totalBytes * STORAGE_BUFFER_RATIO).toLong()
        val requiredBytes = totalBytes + bufferBytes
        val statFs = StatFs(context.filesDir.absolutePath)
        val availableBytes = statFs.availableBytes

        if (availableBytes < requiredBytes) {
            val required = Formatter.formatShortFileSize(context, requiredBytes)
            val available = Formatter.formatShortFileSize(context, availableBytes)
            throw InsufficientStorageException(
                "Not enough free storage for ${spec.displayName}. Required: $required, available: $available."
            )
        }
    }

    private suspend fun downloadFile(
        spec: RemoteModelSpec,
        remoteFile: RemoteModelFile,
        destination: File,
        downloadedBytesBefore: Long,
        totalBytes: Long,
        onProgress: suspend (Long) -> Unit
    ): Long {
        val request = Request.Builder()
            .url(buildFileUrl(spec, remoteFile))
            .get()
            .build()
        val call = httpClient.newCall(request)
        registerActiveCall(spec.id, call)
        val progressEmitter = ProgressEmitter(totalBytes)

        call.execute().use { response ->
            unregisterActiveCall(spec.id, call)
            if (!response.isSuccessful) {
                throw IOException("Download failed with ${response.code}")
            }

            val responseBody = response.body ?: throw IOException("Response body is empty.")
            val responseContentLength = responseBody.contentLength().takeIf { it > 0L }
            val declaredSize = responseContentLength ?: remoteFile.expectedSizeBytes

            destination.parentFile?.mkdirs()
            responseBody.byteStream().use { inputStream ->
                FileOutputStream(destination).use { outputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var writtenBytes = 0L

                    while (true) {
                        ensureNotCancelled(spec.id)
                        val readBytes = inputStream.read(buffer)
                        if (readBytes < 0) {
                            break
                        }
                        outputStream.write(buffer, 0, readBytes)
                        writtenBytes += readBytes
                        progressEmitter.maybeEmit(downloadedBytesBefore + writtenBytes, onProgress)
                    }

                    outputStream.fd.sync()
                    if (responseContentLength != null && writtenBytes != declaredSize) {
                        throw IOException("Downloaded file size mismatch.")
                    }
                    progressEmitter.forceEmit(downloadedBytesBefore + writtenBytes, onProgress)
                    return writtenBytes
                }
            }
        }
    }

    private fun installModel(
        spec: RemoteModelSpec,
        downloadedFiles: Map<RemoteModelFile, File>
    ) {
        ensureNotCancelled(spec.id)
        if (spec.files.size == 1) {
            installSingleFile(spec, downloadedFiles)
        } else {
            installDirectory(spec, downloadedFiles)
        }
    }

    private fun installSingleFile(
        spec: RemoteModelSpec,
        downloadedFiles: Map<RemoteModelFile, File>
    ) {
        val remoteFile = spec.files.single()
        val sourceFile = downloadedFiles.getValue(remoteFile)
        val targetFile = File(context.filesDir, remoteFile.targetRelativePath)
        val parentDir = targetFile.parentFile ?: throw IOException("Target parent is unavailable.")
        val stagingFile = File(parentDir, "${targetFile.name}.downloading")
        val backupFile = File(parentDir, "${targetFile.name}.backup")

        parentDir.mkdirs()
        stagingFile.delete()
        backupFile.delete()

        moveFile(sourceFile, stagingFile)

        try {
            ensureNotCancelled(spec.id)
            if (targetFile.exists()) {
                moveFile(targetFile, backupFile)
            }
            moveFile(stagingFile, targetFile)
            backupFile.delete()
        } catch (error: Throwable) {
            if (stagingFile.exists()) {
                stagingFile.delete()
            }
            if (backupFile.exists() && !targetFile.exists()) {
                moveFile(backupFile, targetFile)
            }
            throw error
        }
    }

    private fun installDirectory(
        spec: RemoteModelSpec,
        downloadedFiles: Map<RemoteModelFile, File>
    ) {
        val installRoot = commonInstallRoot(spec)
        val targetDir = File(context.filesDir, installRoot)
        val parentDir = targetDir.parentFile ?: throw IOException("Target parent is unavailable.")
        val stagingDir = File(parentDir, "${targetDir.name}.downloading")
        val backupDir = File(parentDir, "${targetDir.name}.backup")

        stagingDir.deleteRecursively()
        backupDir.deleteRecursively()
        stagingDir.mkdirs()

        spec.files.forEach { remoteFile ->
            ensureNotCancelled(spec.id)
            val sourceFile = downloadedFiles.getValue(remoteFile)
            when (remoteFile.archiveFormat) {
                RemoteArchiveFormat.None -> {
                    val relativeTargetPath = remoteFile.targetRelativePath
                        .removePrefix("$installRoot/")
                    moveFile(sourceFile, File(stagingDir, relativeTargetPath))
                }
                RemoteArchiveFormat.TarBz2 -> {
                    extractTarBz2Archive(sourceFile, stagingDir)
                }
            }
        }

        try {
            ensureNotCancelled(spec.id)
            if (targetDir.exists()) {
                moveDirectory(targetDir, backupDir)
            }
            moveDirectory(stagingDir, targetDir)
            backupDir.deleteRecursively()
        } catch (error: Throwable) {
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
            if (backupDir.exists() && !targetDir.exists()) {
                moveDirectory(backupDir, targetDir)
            }
            throw error
        }
    }

    private fun commonInstallRoot(spec: RemoteModelSpec): String {
        val pathParts = spec.files.map { file ->
            file.targetRelativePath
                .substringBeforeLast('/', missingDelimiterValue = "")
                .split('/')
                .filter { it.isNotBlank() }
        }

        if (pathParts.isEmpty()) {
            throw IOException("Target install root is unavailable.")
        }

        val prefix = mutableListOf<String>()
        val shortest = pathParts.minOf { it.size }
        for (index in 0 until shortest) {
            val segment = pathParts.first()[index]
            if (pathParts.all { it[index] == segment }) {
                prefix += segment
            } else {
                break
            }
        }

        if (prefix.isEmpty()) {
            throw IOException("Target install root is unavailable.")
        }

        return prefix.joinToString("/")
    }

    private fun extractTarBz2Archive(
        sourceFile: File,
        outputDirectory: File
    ) {
        BZip2CompressorInputStream(sourceFile.inputStream().buffered()).use { bzipInput ->
            TarArchiveInputStream(bzipInput).use { tarInput ->
                while (true) {
                    val entry = tarInput.nextTarEntry ?: break
                    val targetFile = resolveArchiveEntry(outputDirectory, entry)
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                        continue
                    }

                    targetFile.parentFile?.mkdirs()
                    copyArchiveEntry(tarInput, targetFile)
                }
            }
        }
    }

    private fun resolveArchiveEntry(
        outputDirectory: File,
        entry: TarArchiveEntry
    ): File {
        val targetFile = File(outputDirectory, entry.name)
        val canonicalOutputDirectory = outputDirectory.canonicalFile
        val canonicalTargetFile = targetFile.canonicalFile

        if (!canonicalTargetFile.path.startsWith(canonicalOutputDirectory.path + File.separator) &&
            canonicalTargetFile != canonicalOutputDirectory
        ) {
            throw IOException("Archive entry is outside the target directory.")
        }

        return canonicalTargetFile
    }

    private fun copyArchiveEntry(
        inputStream: InputStream,
        destination: File
    ) {
        FileOutputStream(destination).use { outputStream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val readBytes = inputStream.read(buffer)
                if (readBytes <= 0) {
                    break
                }
                outputStream.write(buffer, 0, readBytes)
            }
            outputStream.fd.sync()
        }
    }

    private fun buildFileUrl(
        spec: RemoteModelSpec,
        remoteFile: RemoteModelFile
    ): HttpUrl {
        remoteFile.directUrl?.let { directUrl ->
            return directUrl.toHttpUrl()
        }

        return HttpUrl.Builder()
            .scheme("https")
            .host("huggingface.co")
            .addPathSegments(spec.repoId)
            .addPathSegment("resolve")
            .addPathSegment(spec.revision)
            .apply {
                remoteFile.remotePath.split('/').forEach { segment ->
                    addPathSegment(segment)
                }
            }
            .addQueryParameter("download", "true")
            .build()
    }

    private fun registerActiveCall(modelId: String, call: Call) {
        activeCalls[modelId] = call
    }

    private fun unregisterActiveCall(modelId: String, call: Call? = null) {
        if (call == null || activeCalls[modelId] === call) {
            activeCalls.remove(modelId)
        }
    }

    private fun cancellationFlag(modelId: String): AtomicBoolean {
        return cancelFlags.getOrPut(modelId) { AtomicBoolean(false) }
    }

    private fun clearCancellation(modelId: String) {
        cancellationFlag(modelId).set(false)
    }

    private fun ensureNotCancelled(modelId: String) {
        if (cancellationFlag(modelId).get()) {
            throw DownloadCancelledException()
        }
    }

    private fun calculateProgress(
        downloadedBytes: Long,
        totalBytes: Long
    ): Int? {
        if (totalBytes <= 0L) {
            return null
        }
        val percent = ((downloadedBytes * 100L) / totalBytes).toInt()
        return percent.coerceIn(0, 100)
    }

    private fun moveFile(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (source.renameTo(target)) {
            return
        }
        source.copyTo(target, overwrite = true)
        if (!source.delete()) {
            throw IOException("Failed to remove source file after copy.")
        }
    }

    private fun moveDirectory(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (source.renameTo(target)) {
            return
        }
        source.copyRecursively(target, overwrite = true)
        source.deleteRecursively()
    }

    private fun Throwable.toUserMessage(spec: RemoteModelSpec): String {
        return when (this) {
            is InsufficientStorageException -> message ?: "Not enough free storage."
            is IOException -> "Failed to download ${spec.displayName}. Check connection and retry."
            else -> "Failed to install ${spec.displayName}."
        }
    }

    private inner class ProgressEmitter(
        private val totalBytes: Long
    ) {
        private var lastPercent: Int? = null
        private var lastEmitAtMs = 0L

        suspend fun maybeEmit(
            downloadedBytes: Long,
            onProgress: suspend (Long) -> Unit
        ) {
            val now = SystemClock.elapsedRealtime()
            val percent = calculateProgress(downloadedBytes, totalBytes)
            val shouldEmit = lastPercent == null ||
                percent != lastPercent ||
                now - lastEmitAtMs >= PROGRESS_EMIT_INTERVAL_MS

            if (shouldEmit) {
                lastPercent = percent
                lastEmitAtMs = now
                onProgress(downloadedBytes)
            }
        }

        suspend fun forceEmit(
            downloadedBytes: Long,
            onProgress: suspend (Long) -> Unit
        ) {
            lastPercent = calculateProgress(downloadedBytes, totalBytes)
            lastEmitAtMs = SystemClock.elapsedRealtime()
            onProgress(downloadedBytes)
        }
    }

    private class DownloadCancelledException : IOException()

    private class InsufficientStorageException(message: String) : IOException(message)

    private companion object {
        const val STORAGE_BUFFER_RATIO = 0.1
        const val PROGRESS_EMIT_INTERVAL_MS = 250L
    }
}
