package cz.hspinovace.psmf.tools.leagueimport

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/** Where pages come from. The parser tests hand in saved pages; a run hands in [PoliteCachedFetcher]. */
fun interface PageSource {
    /** The page at [path], a site-relative path such as `/hriste/`. */
    fun get(path: String): String
}

/**
 * psmf.cz, asked politely (DECISIONS 2026-10-07):
 *
 * - **sequential**: one request at a time, never in parallel;
 * - **at least [minimumGap] between requests**, measured from the end of
 *   the previous one;
 * - **a user-agent naming the project**;
 * - **every page cached** in [cacheDir], which is git-ignored, so a
 *   development re-run does not touch the site. A cached page is never
 *   fetched again; delete it to refresh it.
 *
 * [offline] refuses to fetch at all: a page not in the cache is an error.
 */
class PoliteCachedFetcher(
    private val cacheDir: File,
    private val offline: Boolean = false,
    private val minimumGap: Duration = Duration.ofMillis(MINIMUM_GAP_MILLIS),
) : PageSource {
    /** Requests that actually reached psmf.cz in this run. */
    var networkRequests: Int = 0
        private set

    /** Pages served from the cache in this run. */
    var cacheHits: Int = 0
        private set

    /**
     * When the oldest page read in this run was fetched: a cached page's
     * file time, or the moment of the request. Null before anything is read.
     */
    var oldestPageFetchedAt: Instant? = null
        private set

    /**
     * [oldestPageFetchedAt] as a Prague date: the day the data stands for.
     * A run from the cache is as old as the cache, not as the day it ran.
     */
    fun fetchedOn(): kotlinx.datetime.LocalDate? =
        oldestPageFetchedAt?.let { kotlinx.datetime.LocalDate.parse(it.atZone(PRAGUE).toLocalDate().toString()) }

    private val client =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build()

    private var lastRequestEndedAt: Long? = null

    override fun get(path: String): String {
        val cached = File(cacheDir, cacheName(path))
        if (cached.isFile && cached.length() > 0) {
            cacheHits++
            noteFetchedAt(Instant.ofEpochMilli(cached.lastModified()))
            return cached.readText()
        }
        check(!offline) { "$path is not in the cache, and --offline forbids fetching it" }

        waitForTheGap()
        val request =
            HttpRequest
                .newBuilder(URI.create(SITE + path))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .GET()
                .build()
        val response =
            try {
                client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
            } finally {
                lastRequestEndedAt = System.nanoTime()
                networkRequests++
            }
        log("${OffsetDateTime.now()} ${response.statusCode()} $path (importer)")
        check(response.statusCode() == HTTP_OK) { "psmf.cz answered ${response.statusCode()} for $path" }

        cacheDir.mkdirs()
        cached.writeText(response.body())
        noteFetchedAt(Instant.now())
        return response.body()
    }

    private fun noteFetchedAt(instant: Instant) {
        oldestPageFetchedAt = minOf(oldestPageFetchedAt ?: instant, instant)
    }

    private fun waitForTheGap() {
        val last = lastRequestEndedAt ?: return
        val waited = Duration.ofNanos(System.nanoTime() - last)
        val remaining = minimumGap - waited
        if (!remaining.isNegative) Thread.sleep(remaining.toMillis() + 1)
    }

    private fun log(line: String) {
        cacheDir.mkdirs()
        File(cacheDir, "requests.log").appendText(line + "\n")
    }

    companion object {
        const val SITE = "https://www.psmf.cz"

        /** Names the project and says how it behaves, so the site's owners can see what it is. */
        const val USER_AGENT =
            "psmf-zou-app-league-import/0.2.0 " +
                "(internal testing tool for the PSMF Zapis o utkani app; sequential, 1 request/s, cached)"

        /** One second is the floor the decision sets; a little more costs nothing. */
        const val MINIMUM_GAP_MILLIS = 1_200L
        const val TIMEOUT_SECONDS = 30L
        private const val HTTP_OK = 200
        private val PRAGUE: ZoneId = ZoneId.of("Europe/Prague")

        /** `/souteze/a/6-k/` -> `souteze__a__6-k.html`. Readable, flat, and one file per page. */
        fun cacheName(path: String): String {
            val name =
                path
                    .trim('/')
                    .replace("/", "__")
                    .ifEmpty { "index" }
            return "$name.html"
        }
    }
}
