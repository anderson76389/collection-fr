import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Flame Comics"
    pkgName = "en.flamecomics"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://flamecomics.xyz"
        id = 8531542650987673943L
    }
}
