package com.portico.android.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import com.portico.android.BuildConfig
import com.portico.android.domain.PortfolioDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

data class StoredPortfolioFile(
    val pathname: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long
)

/**
 * Authenticated client for Portico's private Vercel Blob bridge.
 * The Blob credential remains server-side; Android sends only the active
 * Clerk session and the file body.
 */
object PorticoFiles {
    const val MAX_FILE_BYTES = 4_000_000
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun upload(
        context: Context,
        source: Uri,
        resourceId: String,
        kind: String = "documents"
    ): StoredPortfolioFile = withContext(Dispatchers.IO) {
        val metadata = metadata(context, source)
        val bytes = context.contentResolver.openInputStream(source)?.use(::readBounded)
            ?: error("The selected file can no longer be read")
        val contentType = context.contentResolver.getType(source)
            ?.substringBefore(';')
            ?.lowercase()
            ?: contentTypeFromName(metadata.first)

        val connection = authenticatedConnection(apiUrl(), "POST").apply {
            doOutput = true
            setFixedLengthStreamingMode(bytes.size)
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("X-Portico-File-Kind", kind)
            setRequestProperty("X-Portico-Resource-Id", resourceId)
            setRequestProperty(
                "X-Portico-File-Name",
                Base64.encodeToString(
                    metadata.first.toByteArray(StandardCharsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
            )
        }

        try {
            connection.outputStream.use { it.write(bytes) }
            val body = connection.responseText()
            if (connection.responseCode !in 200..299) error(apiError(connection.responseCode, body))
            val payload = json.parseToJsonElement(body).jsonObject
            StoredPortfolioFile(
                pathname = payload.getValue("pathname").jsonPrimitive.content,
                fileName = payload["fileName"]?.jsonPrimitive?.content ?: metadata.first,
                contentType = payload["contentType"]?.jsonPrimitive?.content ?: contentType,
                sizeBytes = payload["size"]?.jsonPrimitive?.long ?: bytes.size.toLong()
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(context: Context, document: PortfolioDocument): Uri = withContext(Dispatchers.IO) {
        check(document.storagePath.isNotBlank()) { "This document has no stored file" }
        val query = URLEncoder.encode(document.storagePath, StandardCharsets.UTF_8.name())
        val connection = authenticatedConnection("${apiUrl()}?path=$query", "GET")
        try {
            if (connection.responseCode !in 200..299) {
                error(apiError(connection.responseCode, connection.responseText()))
            }
            val directory = File(context.cacheDir, "documents").apply { mkdirs() }
            val target = File(directory, "${safeName(document.id)}-${safeName(document.fileName)}")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FILE_BYTES) error("Downloaded file exceeds the Portico file limit")
                        output.write(buffer, 0, read)
                    }
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Resolves a stored photograph to a file Coil can render.
     *
     * Blob objects are private and need the session header, so an image URL
     * cannot simply be handed to the image loader. The bytes are fetched once
     * and cached on disk under a name derived from the pathname; subsequent
     * reads skip the network entirely.
     */
    suspend fun photo(context: Context, pathname: String): Uri = withContext(Dispatchers.IO) {
        check(pathname.isNotBlank()) { "This photograph has no stored file" }
        val directory = File(context.cacheDir, "photos").apply { mkdirs() }
        val target = File(directory, "${pathname.hashCode().toUInt()}.img")
        if (target.isFile && target.length() > 0) {
            return@withContext Uri.fromFile(target)
        }

        val query = URLEncoder.encode(pathname, StandardCharsets.UTF_8.name())
        val connection = authenticatedConnection("${apiUrl()}?path=$query", "GET")
        try {
            if (connection.responseCode !in 200..299) {
                error(apiError(connection.responseCode, connection.responseText()))
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            Uri.fromFile(target)
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        } finally {
            connection.disconnect()
        }
    }

    /** True when [reference] is a blob pathname rather than a local picker URI. */
    fun isStoredReference(reference: String): Boolean =
        reference.isNotBlank() &&
            !reference.startsWith("content://") &&
            !reference.startsWith("file://") &&
            !reference.startsWith("http")

    suspend fun delete(pathname: String) = withContext(Dispatchers.IO) {
        if (pathname.isBlank() || pathname.startsWith("content://")) return@withContext
        val connection = authenticatedConnection(apiUrl(), "DELETE").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            val escaped = pathname.replace("\\", "\\\\").replace("\"", "\\\"")
            connection.outputStream.use { it.write("{\"path\":\"$escaped\"}".toByteArray()) }
            if (connection.responseCode !in 200..299) {
                error(apiError(connection.responseCode, connection.responseText()))
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun authenticatedConnection(url: String, method: String): HttpURLConnection {
        val token = FirebaseBackend.sessionToken()
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            useCaches = false
        }
    }

    private fun metadata(context: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }
                        ?.let { name = cursor.getString(it) ?: name }
                    cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }
                        ?.let { size = cursor.getLong(it) }
                }
            }
        if (size > MAX_FILE_BYTES) error("Files must be 4 MB or smaller")
        return name to size
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_FILE_BYTES) error("Files must be 4 MB or smaller")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun HttpURLConnection.responseText(): String =
        (if (responseCode in 200..299) inputStream else errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()

    private fun apiError(status: Int, body: String): String {
        val code = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull()
        return when (code) {
            "file_too_large" -> "Files must be 4 MB or smaller"
            "unsupported_file_type" -> "Choose a PDF, JPEG, PNG, or WebP file"
            "file_not_found" -> "This private file is no longer available"
            "server_not_configured" -> "Private file storage is not configured yet"
            else -> "Private file request failed ($status)"
        }
    }

    private fun apiUrl(): String = BuildConfig.PORTICO_FILE_API_URL
        .takeIf(String::isNotBlank)
        ?: error("Private file service URL is missing")

    private fun safeName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .take(120)
        .ifBlank { "document" }

    private fun contentTypeFromName(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }
}
