import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SayManhwa"
    pkgName = "all.saymanhwa"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl {
            custom("https://saymanhwa.com")
        }
    }

    deeplink {
        path("/fr/series/..*")
    }
}
