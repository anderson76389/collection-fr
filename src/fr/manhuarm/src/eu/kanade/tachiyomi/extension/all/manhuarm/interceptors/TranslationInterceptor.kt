package eu.kanade.tachiyomi.extension.all.manhuarm.interceptors

import eu.kanade.tachiyomi.extension.all.manhuarm.Dialog
import eu.kanade.tachiyomi.extension.all.manhuarm.Language
import eu.kanade.tachiyomi.extension.all.manhuarm.translator.TranslatorEngine
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class TranslationInterceptor(
    private val settings: () -> Language,
    private val translator: () -> TranslatorEngine,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val language = settings()

        if (language.disableTranslator || language.target == language.origin) return chain.proceed(request)
        val dialogues = request.url.fragment?.takeIf { it.startsWith("[") }?.parseAs<List<Dialog>>()
            ?: return chain.proceed(request)
        if (dialogues.all { it.textFor(language.target) != null }) return chain.proceed(request)

        val engine = translator()
        val translated = runBlocking(Dispatchers.IO) {
            dialogues.map { dialog ->
                async {
                    if (dialog.textFor(language.target) != null || dialog.text.isBlank()) {
                        dialog
                    } else {
                        dialog.translated(language.target, engine.translate(language.origin, language.target, dialog.text))
                    }
                }
            }.awaitAll()
        }

        val newRequest = request.newBuilder()
            .url(request.url.newBuilder().fragment(translated.toJsonString()).build())
            .build()

        return chain.proceed(newRequest)
    }
}
