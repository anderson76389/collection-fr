import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Origines"
    versionCode = 55
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "origines"

    source {
        // Keep the existing Mihon source ID to preserve library and reading history.
        lang = "all"
        id = 3875672872924535086L
        baseUrl = "https://hentai-origines.com"
    }
}
