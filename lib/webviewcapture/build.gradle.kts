import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(kei.plugins.library)
}

extensions.configure<LibraryExtension> {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.named("androidTest") {
        java.directories.clear()
        java.directories.add("tests/android")
        kotlin.directories.clear()
        kotlin.directories.add("tests/android")
        manifest.srcFile("tests/AndroidManifest.xml")
    }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(libs.coroutines.android)
}
