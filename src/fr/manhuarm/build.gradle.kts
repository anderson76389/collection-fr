import com.android.build.api.dsl.ApplicationExtension
import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhuarm"
    versionCode = 6
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    pkgName = "all.manhuarm"

    source {
        lang = "fr"
        baseUrl = "https://manhuarmtl.com"
        id = 8537659881437820380L
    }

    deeplink {
        path("/manga/..*")
    }
}

dependencies {
    implementation(project(":lib:i18n"))
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
