import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Phenix Scans"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "Phenix Scans"
        lang = "fr"
        baseUrl = "https://phenix-scans.com"
    }
}
