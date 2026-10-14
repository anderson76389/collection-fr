import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaWeb"
    versionCode = 15
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    pkgName = "es.manhwaweb"

    source {
        lang = "all"
        id = 5943212552955476721L
        baseUrl = "https://manhwaweb.com"
    }

    deeplink {
        path("/manhwa/..*")
    }
}
