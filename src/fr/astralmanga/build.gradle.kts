import com.android.build.api.dsl.ApplicationExtension
import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Astral-Manga"
    versionCode = 53
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "AstralManga"
        lang = "fr"
        baseUrl = "https://astral-manga.fr"
        versionId = 2
    }
}

extensions.configure<ApplicationExtension> {
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
    testImplementation(libs.okhttp.core)
}

// Source metadata is generated for production classes, not for parser-only unit tests.
tasks.matching { it.name == "kspDebugUnitTestKotlin" }.configureEach {
    enabled = false
}
