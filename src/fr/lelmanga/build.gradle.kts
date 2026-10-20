import com.android.build.api.dsl.ApplicationExtension
import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lelmanga"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "fr"
        baseUrl = "https://www.lelmanga.com"
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
    testImplementation(libs.jsoup)
    testImplementation(libs.coroutines.core)
}

// Source metadata is generated for production classes, not for parser-only unit tests.
tasks.matching { it.name == "kspDebugUnitTestKotlin" }.configureEach {
    enabled = false
}
