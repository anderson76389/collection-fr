package eu.kanade.tachiyomi.extension.all.manhuarm.translator.bing

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Parallel bubble translations share one session instead of each loading the translator page.
internal class BingTokenStore(private val load: suspend () -> TokenGroup) {
    private val mutex = Mutex()
    private var current = TokenGroup()

    suspend fun get(failed: TokenGroup? = null): TokenGroup = mutex.withLock {
        if (!current.isValid() || current === failed) current = load()
        current
    }
}
