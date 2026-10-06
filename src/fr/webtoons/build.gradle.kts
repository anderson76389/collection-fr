import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Webtoons.com"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    pkgName = "all.webtoons"

    source {
        lang = "fr"
        baseUrl = "https://www.webtoons.com"
    }

    deeplink {
        host("webtoons.com")
        host("www.webtoons.com")
        host("m.webtoons.com")
        path("/.*/.*/.*/..*")
        path("/.*/.*/.*/.*/..*")
    }
}

dependencies {
    implementation(project(":lib:textinterceptor"))
}
