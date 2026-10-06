import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kagane"
    versionCode = 33
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    pkgName = "all.kagane"

    listOf("fr").forEach {
        source {
            lang = it
            baseUrl = "https://kagane.to"
        }
    }

    deeplink {
        path("/series/..*")
    }
}
