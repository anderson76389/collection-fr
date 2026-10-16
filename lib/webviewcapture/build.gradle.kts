plugins {
    alias(kei.plugins.library)
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.named("androidTest") {
        java.setSrcDirs(listOf("tests/android"))
        manifest.srcFile("tests/AndroidManifest.xml")
    }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(libs.coroutines.android)
}
