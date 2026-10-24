package eu.kanade.tachiyomi.extension.fr.softepsilonscan

import java.util.Locale

internal fun softEpsilonReaderFailure(reason: String?): String {
    val detail = reason.orEmpty()
    val message = detail.lowercase(Locale.ROOT)
    return when {
        "lecture impossible sur cet appareil" in message || "reader not compatible" in message ||
            "incompatible device" in message || "lecteur non compatible" in message ->
            "Soft Epsilon : le site refuse ce lecteur sur cet appareil. Ouvrez le chapitre dans la WebView de Mihon pour vérifier s’il fonctionne avec votre Android System WebView."
        "canvas is blocked" in message || "canvas blocked" in message ->
            "Soft Epsilon : le lecteur du site refuse l’export des pages. Ouvrez le chapitre dans la WebView de Mihon ; la capture automatique n’est pas disponible."
        "403" in message || "429" in message || "verification" in message || "challenge" in message ||
            ("webview" in message && "pass" in message) ->
            "Soft Epsilon : vérification ou limitation d’accès du site. Ouvrez le chapitre dans la WebView de Mihon et effectuez la vérification demandée avant de réessayer."
        "404" in message || "not found" in message ->
            "Soft Epsilon : chapitre introuvable (404). Vérifiez son adresse dans la WebView de Mihon."
        "reader did not load" in message || "unexpected reader result" in message ||
            "missing page data" in message || "page did not load" in message ->
            "Soft Epsilon : le lecteur v2 n’a pas pu initialiser les images sur cet appareil. Ouvrez le chapitre dans la WebView de Mihon pour vérifier son affichage."
        else -> "Soft Epsilon : échec du lecteur. ${detail.ifBlank { "Vérifiez le chapitre dans la WebView de Mihon." }}"
    }
}
