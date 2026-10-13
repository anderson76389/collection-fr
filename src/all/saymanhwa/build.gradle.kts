import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SayManhwa"
    pkgName = "all.saymanhwa"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "all"
        id = 1085765242853690038L
        baseUrl {
            custom("https://saymanhwa.com")
        }
    }

    deeplink {
        path("/fr/series/..*")
    }
}
