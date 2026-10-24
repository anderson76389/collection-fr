import com.android.build.api.dsl.LibraryExtension
plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:secretstream"))
    api(project(":lib:i18n"))
    implementation(project(":lib:ece"))
}

keiyoushi {
    baseVersionCode = 8
    libVersion = "1.6"
}

extensions.configure<LibraryExtension> {
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
