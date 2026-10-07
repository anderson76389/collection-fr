import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tappytoon"
    pkgName = "all.tappytoon"
    versionCode = 11
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://www.tappytoon.com/fr"
    }
}
