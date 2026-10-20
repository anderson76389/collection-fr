package eu.kanade.tachiyomi.extension.all.manhuarm.translator.bing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BingTokenStoreTest {
    @Test
    fun twentyParallelBubbleTranslationsLoadOneSession() = runBlocking {
        var calls = 0
        val release = CompletableDeferred<Unit>()
        val session = TokenGroup("token", "key", "iid", "ig")
        val store = BingTokenStore {
            calls++
            release.await()
            session
        }
        val requests = (1..20).map { async(start = CoroutineStart.UNDISPATCHED) { store.get() } }
        assertEquals(1, calls)
        release.complete(Unit)
        requests.awaitAll().forEach { assertSame(session, it) }
        assertEquals(1, calls)
    }

    @Test
    fun simultaneousFailuresOfOneSessionTriggerOnlyOneRefresh() = runBlocking {
        var calls = 0
        val store = BingTokenStore {
            calls++
            TokenGroup("token-$calls", "key", "iid", "ig")
        }
        val old = store.get()
        val requests = (1..20).map { async(start = CoroutineStart.UNDISPATCHED) { store.get(failed = old) } }
        val renewed = requests.awaitAll()
        assertEquals(2, calls)
        renewed.forEach { assertSame(renewed.first(), it) }
        assertEquals("token-2", renewed.first().token)
    }
}
