import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhuarm"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf("fr", "all").forEach {
        source {
            lang = it
            baseUrl = "https://manhuarmtl.com"
        }
    }

    deeplink {
        path("/manga/..*")
    }
}

dependencies {
    implementation(project(":lib:i18n"))
}
