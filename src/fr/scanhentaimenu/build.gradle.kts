import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "X-Manga"
    versionCode = 64
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://x-manga.org"
        id = 4153742697148883998L
    }
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    sourceSets.named("test") {
        java.directories.clear()
        java.directories.add("tests")
        kotlin.directories.clear()
        kotlin.directories.add("tests")
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.stdlib)
    testImplementation(libs.jsoup)
}

tasks.matching { it.name == "kspDebugUnitTestKotlin" }.configureEach {
    enabled = false
}
