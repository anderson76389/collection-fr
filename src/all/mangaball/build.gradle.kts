import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Ball"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf("fr", "all").forEach {
        source {
            lang = it
            baseUrl = "https://mangaball.net"
        }
    }

    deeplink {
        host("mangaball.net")
        path("/title-detail/..*")
        path("/chapter-detail/..*")
    }
}
