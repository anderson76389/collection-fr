import com.android.build.api.dsl.ApplicationExtension
import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Epsilon Scan"
    versionCode = 13
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "pam"

    source {
        lang = "fr"
        baseUrl = "https://epsilonsoft.to"
        id = 4969929389259348100L
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
}

tasks.matching { it.name == "kspDebugUnitTestKotlin" }.configureEach {
    enabled = false
}
