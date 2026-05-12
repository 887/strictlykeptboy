package com.eight87.strictlykeptboy.resolver

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

class ResolverCacheTest {

    private fun stubSchedule(hash: String) = RenderedSchedule(
        rangeFrom = java.time.ZonedDateTime.parse("2026-05-11T00:00:00Z"),
        rangeTo = java.time.ZonedDateTime.parse("2026-05-12T00:00:00Z"),
        viewMode = ViewMode.Day,
        days = emptyList(),
        sourceDigest = hash,
    )

    @Test fun cacheHitOnIdenticalKey() = runTest {
        val cache = ResolverCache(capacity = 4)
        val key = ResolverCache.Key(
            "hash-1", ViewMode.Day,
            DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")),
        )
        var hits = 0
        val v1 = cache.getOrPut(key) { hits++; stubSchedule("hash-1") }
        val v2 = cache.getOrPut(key) { hits++; stubSchedule("hash-1") }
        assertSame(v1, v2)
        assertEquals(1, hits)
    }

    @Test fun cacheMissOnSnapshotHashChange() = runTest {
        val cache = ResolverCache(capacity = 4)
        val range = DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11"))
        val k1 = ResolverCache.Key("hash-1", ViewMode.Day, range)
        val k2 = ResolverCache.Key("hash-2", ViewMode.Day, range)
        cache.put(k1, stubSchedule("hash-1"))
        assertNull(cache.get(k2))
        cache.put(k2, stubSchedule("hash-2"))
        assertNotNull(cache.get(k2))
    }

    @Test fun lruEvictionAtCapacity() = runTest {
        val cache = ResolverCache(capacity = 2)
        val k1 = ResolverCache.Key("a", ViewMode.Day, DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")))
        val k2 = ResolverCache.Key("b", ViewMode.Day, DateRange(LocalDate.parse("2026-05-12"), LocalDate.parse("2026-05-12")))
        val k3 = ResolverCache.Key("c", ViewMode.Day, DateRange(LocalDate.parse("2026-05-13"), LocalDate.parse("2026-05-13")))
        cache.put(k1, stubSchedule("a"))
        cache.put(k2, stubSchedule("b"))
        // Access k1 to make k2 the eldest.
        assertNotNull(cache.get(k1))
        cache.put(k3, stubSchedule("c"))
        assertEquals(2, cache.size())
        assertNull(cache.get(k2))
        assertNotNull(cache.get(k1))
        assertNotNull(cache.get(k3))
    }

    @Test fun snapshotHash_changesWhenHeadChanges() {
        val s1 = RepoSnapshot(
            repos = listOf(RepoSnapshot.RepoEntry(RepoRef("r1"), "sha-A")),
            calendars = emptyList(),
            todolists = emptyList(),
        )
        val s2 = s1.copy(repos = listOf(RepoSnapshot.RepoEntry(RepoRef("r1"), "sha-B")))
        org.junit.Assert.assertNotEquals(s1.contentHash, s2.contentHash)
    }

    @Test fun invalidateRepoDropsMatchingHashEntries() = runTest {
        val cache = ResolverCache(capacity = 4)
        val k = ResolverCache.Key("old-hash", ViewMode.Day, DateRange(LocalDate.parse("2026-05-11"), LocalDate.parse("2026-05-11")))
        cache.put(k, stubSchedule("old-hash"))
        cache.invalidateRepo(RepoRef("r1"), "old-hash")
        assertNull(cache.get(k))
    }
}
