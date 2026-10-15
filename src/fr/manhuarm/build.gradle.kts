import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhuarm"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    pkgName = "all.manhuarm"

    source {
        lang = "fr"
        baseUrl = "https://manhuarmtl.com"
        id = 8537659881437820380L
    }

    deeplink {
        path("/manga/..*")
    }
}

dependencies {
    implementation(project(":lib:i18n"))
}
