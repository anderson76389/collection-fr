import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ortega Scans"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        // Keep the existing Mihon source ID to preserve library and reading history.
        lang = "all"
        id = 8598370605081191622L
        baseUrl = "https://ortegascans.fr"
    }
}
