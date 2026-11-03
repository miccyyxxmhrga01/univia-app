package com.example.kayara.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SnapshotCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private var now = 1_000_000L
    private fun cache(directory: File = File(temporary.root, "snapshots"),
        maxBytes: Long = 4096, maxEntries: Int = 24) =
        SnapshotCache(directory, { now }, maxAgeMillis = 1000, maxBytes = maxBytes, maxEntries = maxEntries)

    @Test fun snapshotsSurviveAProcessRestartAndStayScopedToUserAndQuery() {
        val cache = cache()
        val payload = """{"documents":[{"data":{"name":"Univia","optional":null}}]}"""
        cache.write("student-a", "posts:latest", payload, cache.revision())

        val restarted = cache()
        assertEquals(payload, restarted.read("student-a", "posts:latest"))
        assertNull(restarted.read("student-b", "posts:latest"))
        assertNull(restarted.read("student-a", "posts:older"))
        assertNull(restarted.read("", "posts:latest"))
    }

    @Test fun expiredSnapshotsAndClockRollbacksAreCacheMisses() {
        val cache = cache()
        cache.write("student", "posts", "[]", cache.revision())
        now += 1000
        assertNull(cache.read("student", "posts"))
        cache.write("student", "posts", "[]", cache.revision())
        now--
        assertNull(cache.read("student", "posts"))
    }

    @Test fun accountChangesAndWritesInvalidateDiskAndRejectInflightResponses() {
        val cache = cache()
        val oldRevision = cache.revision()
        cache.write("student", "posts", "old", oldRevision)
        cache.invalidate()
        cache.write("student", "posts", "in-flight old response", oldRevision)
        assertNull(cache.read("student", "posts"))
        cache.write("student", "posts", "fresh", cache.revision())
        assertEquals("fresh", cache.read("student", "posts"))
    }

    @Test fun invalidationAlsoWorksWhenNextOperationIsAWrite() {
        val cache = cache()
        cache.write("student", "posts", "old", cache.revision())
        cache.invalidate()
        cache.write("student", "profile", "new", cache.revision())
        assertNull(cache.read("student", "posts"))
        assertEquals("new", cache.read("student", "profile"))
    }

    @Test fun corruptAndDeletedFilesAreSafeCacheMisses() {
        val directory = File(temporary.root, "snapshots")
        val cache = cache(directory)
        cache.write("student", "posts", "[]", cache.revision())
        directory.listFiles()!!.single().writeText("interrupted write")
        assertNull(cache.read("student", "posts"))
        directory.listFiles()!!.single().delete()
        assertNull(cache.read("student", "posts"))
    }

    @Test fun entryCountIsBoundedAndOldestSnapshotsAreEvicted() {
        val cache = cache(maxEntries = 2)
        for (key in listOf("oldest", "middle", "newest")) {
            cache.write("student", key, "[]", cache.revision())
            now++
        }
        assertNull(cache.read("student", "oldest"))
        assertEquals("[]", cache.read("student", "middle"))
        assertEquals("[]", cache.read("student", "newest"))
    }

    @Test fun diskUsageIsBoundedAndOversizedSnapshotsAreSkipped() {
        val directory = File(temporary.root, "snapshots")
        val cache = cache(directory, maxBytes = 1024)
        for (index in 0..5) {
            cache.write("student", "posts-$index", "x".repeat(350), cache.revision())
            now++
        }
        assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 1024)
        cache.write("student", "huge", "x".repeat(2048), cache.revision())
        assertNull(cache.read("student", "huge"))
    }

    @Test fun unwritableCacheDoesNotBreakLoading() {
        val blocked = temporary.newFile("not-a-directory")
        val cache = cache(blocked)
        cache.write("student", "posts", "[]", cache.revision())
        assertNull(cache.read("student", "posts"))
    }
}
