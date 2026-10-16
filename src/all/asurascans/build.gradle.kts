import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Asura Scans"
    versionCode = 70
    pkgName = "en.asurascans"
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "all"
        id = 6247824327199706550L
        baseUrl = "https://asurascans.com"
    }

    deeplink {
        path("/comics/..*")
    }
}
