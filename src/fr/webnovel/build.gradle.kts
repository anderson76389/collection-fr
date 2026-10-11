import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WebNovel"
    versionCode = 15
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://www.webnovel.com"
    }

    deeplink {
        path("/comic/..*")
        path("/fr/comic/..*")
    }
}
