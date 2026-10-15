import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Flixnet (CoffeeManga)"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "Flixnet (CoffeeManga)"
        baseUrl = "https://www.coffeemanga.art"
        lang = "all"
    }

    deeplink {
        path("/..*")
    }
}
