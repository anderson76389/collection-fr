import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Olympus Scanlation"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    pkgName = "es.olympusscanlation"

    source {
        lang = "all"
        id = 1596411039134168890L
        baseUrl {
            custom("https://olympusxyz.com")
        }
        versionId = 3
    }
}
