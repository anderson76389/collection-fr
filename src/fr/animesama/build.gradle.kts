import com.android.build.api.dsl.ApplicationExtension
import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AnimeSama"
    versionCode = 21
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://anime-sama.to"
    }

    deeplink {
        host("anime-sama.to")
        path("/catalogue/..*")
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
}

// Source metadata is generated for production classes, not for parser-only unit tests.
tasks.matching { it.name == "kspDebugUnitTestKotlin" }.configureEach {
    enabled = false
}
