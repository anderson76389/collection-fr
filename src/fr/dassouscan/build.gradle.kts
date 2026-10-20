import com.android.build.api.dsl.ApplicationExtension
import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dassou Scan"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://dassouscan.com"
        versionId = 2
    }

    deeplink {
        path("/manga/..*")
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
