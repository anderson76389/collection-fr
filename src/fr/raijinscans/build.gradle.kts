import com.android.build.api.dsl.ApplicationExtension
import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Aniverse"
    versionCode = 74
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        id = 8373267671495516033L
        lang = "fr"
        baseUrl = "https://aniverse.fr"
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
    testImplementation(libs.kotlin.json)
}

tasks.matching { it.name == "kspDebugUnitTestKotlin" }.configureEach {
    enabled = false
}
