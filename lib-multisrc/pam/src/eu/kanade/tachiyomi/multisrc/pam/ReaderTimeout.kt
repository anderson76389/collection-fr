package eu.kanade.tachiyomi.multisrc.pam

internal fun pamReaderTimeoutReason(
    hasPage: Boolean,
    readerV2: Boolean,
    hasReaderResult: Boolean,
    hasPageRequest: Boolean,
): String = when {
    !hasPage ->
        "La page du chapitre ne s'est pas chargée dans la WebView. Vérifiez Cloudflare dans la WebView de Mihon."
    !readerV2 ->
        "Le site n'a pas démarré son lecteur de chapitre dans la WebView."
    !hasReaderResult ->
        "Le lecteur v2 du site a chargé sa page, mais n'a fourni ni manifeste complet ni clé de lecture. Vérifiez son fonctionnement dans la WebView Mihon."
    !hasPageRequest ->
        "Le lecteur v2 a initialisé ses métadonnées, mais aucune requête d'image n'a démarré."
    else ->
        "Le lecteur v2 a fourni ses métadonnées et déclenché des images, mais les en-têtes nécessaires ne correspondent pas au manifeste."
}
