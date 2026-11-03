package com.example.kayara.data

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/** Disposable, user-scoped snapshots. All file access runs on the database IO executor. */
internal class SnapshotCache(
    private val directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxAgeMillis: Long = 60 * 60 * 1000L,
    private val maxBytes: Long = 4 * 1024 * 1024L,
    private val maxEntries: Int = 24
) {
    private val revision = AtomicLong()
    private var clearedRevision = 0L

    fun revision(): Long = revision.get()

    /** Invalidate immediately without doing disk IO on the caller's thread. */
    fun invalidate() { revision.incrementAndGet() }

    @Synchronized fun read(userId: String, key: String): String? {
        if (userId.isBlank()) return null
        return runCatching {
            clearIfNeeded()
            val file = entry(userId, key)
            if (!file.isFile) return null
            if (file.length() > maxBytes) {
                file.delete()
                return null
            }
            val saved = JSONObject(file.readText())
            val age = clock() - saved.getLong("saved_at")
            if (age < 0 || age >= maxAgeMillis) {
                file.delete()
                return null
            }
            saved.getString("payload")
        }.getOrNull()
    }

    @Synchronized fun write(userId: String, key: String, payload: String, expectedRevision: Long) {
        if (userId.isBlank() || revision.get() != expectedRevision) return
        runCatching {
            clearIfNeeded()
            if (revision.get() != expectedRevision) return
            directory.mkdirs()
            val file = entry(userId, key)
            val bytes = JSONObject().put("saved_at", clock()).put("payload", payload)
                .toString().toByteArray(Charsets.UTF_8)
            if (bytes.size > maxBytes / 2) {
                file.delete()
                return
            }
            // Readers share this lock; an interrupted write is treated as a cache miss.
            file.writeBytes(bytes)
            file.setLastModified(clock())
            val files = directory.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }.orEmpty()
            var size = files.sumOf { it.length() }
            var count = files.size
            for (oldest in files) {
                if (size <= maxBytes && count <= maxEntries) break
                val length = oldest.length()
                if (oldest.delete()) { size -= length; count-- }
            }
        }
    }

    private fun clearIfNeeded() {
        val currentRevision = revision.get()
        if (clearedRevision != currentRevision) {
            // Only our own flat cache directory; never recurse into other app files.
            directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
            clearedRevision = currentRevision
        }
    }

    private fun entry(userId: String, key: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$userId\u0000$key".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.json")
    }
}

internal class SnapshotCodec<T>(
    val key: String,
    val encode: (T) -> String,
    val decode: (String) -> Pair<String, T>
)
