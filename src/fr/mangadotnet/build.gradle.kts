import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaDot"
    versionCode = 27
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    pkgName = "en.mangadotnet"

    source {
        lang = "fr"
        baseUrl = "https://mangadot.net"
        id = 6544312035114371248L
    }

    source {
        lang = "all"
        baseUrl = "https://mangadot.net"
    }

    deeplink {
        host("mangadot.net")
        path("/manga/..*")
        path("/chapter/..*")
        path("/volume/..*")
    }
}
