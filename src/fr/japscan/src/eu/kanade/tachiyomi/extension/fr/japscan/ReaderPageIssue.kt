package eu.kanade.tachiyomi.extension.fr.japscan

import java.util.Locale

internal fun japscanReaderPageIssue(title: String): String? {
    val normalized = title.trim().trim('"').lowercase(Locale.ROOT)
    return when {
        listOf("just a moment", "attention required", "sorry, you have been blocked", "cloudflare", "access denied", "403 forbidden")
            .any(normalized::contains) ->
            "Japscan : accès refusé ou vérification Cloudflare. Ouvrez ce chapitre dans la WebView de Mihon, validez la vérification manuellement, puis réessayez."
        normalized.startsWith("404") || normalized == "not found" || normalized.contains("404 not found") ->
            "Japscan : chapitre introuvable (404). Vérifiez dans la WebView si le chapitre existe encore et si son adresse a changé."
        else -> null
    }
}
