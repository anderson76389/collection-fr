package eu.kanade.tachiyomi.extension.fr.dassouscan

import kotlinx.coroutines.CompletableDeferred
import org.jsoup.nodes.Document

internal class RecentDocuments(private val clock: () -> Long = System::nanoTime) {
    private class Entry(val document: Document, val savedAt: Long)
    private val documents = LinkedHashMap<String, Entry>()
    private val pending = mutableMapOf<String, CompletableDeferred<Document>>()

    suspend fun get(url: String, load: suspend () -> Document): Document {
        val request: CompletableDeferred<Document>
        val owner: Boolean
        synchronized(this) {
            documents[url]?.takeIf { clock() - it.savedAt < 20_000_000_000L }?.let { return it.document.clone() }
            val existing = pending[url]
            owner = existing == null
            request = existing ?: CompletableDeferred<Document>().also { pending[url] = it }
        }
        if (!owner) return request.await().clone()
        try {
            val document = load()
            synchronized(this) {
                documents.remove(url)
                documents[url] = Entry(document, clock())
                while (documents.size > 6) documents.remove(documents.keys.first())
            }
            request.complete(document)
            return document.clone()
        } catch (error: Throwable) {
            request.completeExceptionally(error)
            throw error
        } finally {
            synchronized(this) { pending.remove(url) }
        }
    }
}
