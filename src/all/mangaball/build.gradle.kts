import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Ball"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf("fr", "all").forEach {
        source {
            lang = it
            baseUrl = "https://mangaball.com"
        }
    }

    deeplink {
        host("mangaball.net")
        host("mangaball.com")
        path("/title-detail/..*")
        path("/chapter-detail/..*")
    }
}
