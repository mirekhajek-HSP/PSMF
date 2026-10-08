package cz.hspinovace.psmf.tools.leagueimport

import kotlinx.datetime.LocalDate
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The cache, and what it says about how old the data is. Never touches the
 * site: every fetcher here is offline.
 */
class PoliteCachedFetcherTest {
    private val cache: File = Files.createTempDirectory("psmf-cache-").toFile()

    @AfterTest
    fun cleanUp() {
        cache.deleteRecursively()
    }

    private fun cachedPage(
        path: String,
        fetchedAt: String,
    ) {
        File(cache, PoliteCachedFetcher.cacheName(path)).apply {
            writeText("<html></html>")
            setLastModified(Instant.parse(fetchedAt).toEpochMilli())
        }
    }

    @Test
    fun aRunFromTheCacheIsAsOldAsItsOldestPageNotAsTheDayItRan() {
        // A re-run the next day, from the cache, must not make the yellow
        // counts look a day fresher than the pages they were read from.
        cachedPage("/hriste/", "2026-10-07T16:40:00Z")
        // 00:30 on the 6th in Prague, though still the 5th in UTC.
        cachedPage("/souteze/a/6-k/", "2026-10-05T22:30:00Z")
        val fetcher = PoliteCachedFetcher(cache, offline = true)
        assertNull(fetcher.fetchedOn(), "nothing read yet")

        fetcher.get("/hriste/")
        fetcher.get("/souteze/a/6-k/")

        assertEquals(LocalDate(2026, 10, 6), fetcher.fetchedOn())
        assertEquals(0, fetcher.networkRequests)
        assertEquals(2, fetcher.cacheHits)
    }
}
