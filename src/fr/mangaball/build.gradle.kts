import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Ball"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    pkgName = "all.mangaball"

    source {
        lang = "fr"
        baseUrl = "https://mangaball.com"
        id = 4804845656972446863L
    }

    deeplink {
        host("mangaball.net")
        host("mangaball.com")
        path("/title-detail/..*")
        path("/chapter-detail/..*")
    }
}
