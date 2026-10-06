import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lunar Manga"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    pkgName = "all.lunaranime"

    val languages = listOf("fr")

    languages.forEach { language ->
        source {
            baseUrl = "https://lunarx.to"
            lang = language
        }
    }
}

dependencies {
    implementation(project(":lib:cryptoaes"))
}
