import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sushiscan.net"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        name = "Sushiscan.net"
        lang = "fr"
        baseUrl = "https://sushiscan.net"
        id = 3196884165456788667L
    }
}

dependencies {
    implementation(project(":lib:randomua"))
}
