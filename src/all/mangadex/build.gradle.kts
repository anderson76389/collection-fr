import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaDex"
    versionCode = 8
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "all"
        id = 4505830566611664829L
        baseUrl = "https://mangadex.org"
    }

    deeplink {
        host("mangadex.org")
        host("canary.mangadex.dev")
        path("/title/..*")
        path("/manga/..*")
        path("/chapter/..*")
        path("/group/..*")
        path("/author/..*")
        path("/user/..*")
        path("/list/..*")
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}
