package eu.kanade.tachiyomi.extension.fr.lelmanga

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class RecentDocumentsTest {
    @Test
    fun concurrentRequestsShareOneFetchAndGetIndependentDocuments() = runBlocking {
        val cache = RecentDocuments()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val load: suspend () -> org.jsoup.nodes.Document = {
            calls++
            release.await()
            Jsoup.parse("<h1>Original</h1>")
        }
        val first = async(start = CoroutineStart.UNDISPATCHED) { cache.get("work", load) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { cache.get("work", load) }
        assertEquals(1, calls)
        release.complete(Unit)
        first.await().selectFirst("h1")!!.text("Changed")
        assertEquals("Original", second.await().selectFirst("h1")!!.text())
        assertEquals("Original", cache.get("work", load).selectFirst("h1")!!.text())
        assertEquals(1, calls)
    }

    @Test
    fun documentsExpireAfterTwentySeconds() = runBlocking {
        var time = 0L
        var calls = 0
        val cache = RecentDocuments { time }
        val load: suspend () -> org.jsoup.nodes.Document = {
            calls++
            Jsoup.parse("$calls")
        }
        cache.get("work", load)
        time = 19_999_999_999L
        cache.get("work", load)
        assertEquals(1, calls)
        time = 20_000_000_000L
        cache.get("work", load)
        assertEquals(2, calls)
    }

    @Test
    fun failedResponsesDoNotPoisonTheNextAttempt() = runBlocking {
        val cache = RecentDocuments()
        var failures = 0
        try {
            cache.get("work") { throw IOException("unavailable") }
        } catch (_: IOException) {
            failures++
        }
        assertEquals(1, failures)
        assertEquals("Recovered", cache.get("work") { Jsoup.parse("<h1>Recovered</h1>") }.selectFirst("h1")!!.text())
    }

    @Test
    fun onlySixDocumentsAreRetained() = runBlocking {
        val cache = RecentDocuments { 0L }
        var calls = 0
        val load: suspend () -> org.jsoup.nodes.Document = {
            calls++
            Jsoup.parse("$calls")
        }
        (0..6).forEach { cache.get("work-$it", load) }
        cache.get("work-0", load)
        assertEquals(8, calls)
    }
}
