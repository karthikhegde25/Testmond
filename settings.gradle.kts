pluginManagement {
    repositories {
        google()
        mavenCentral()
        // Fallback only: Maven Central sometimes answers GitHub's shared build machines with
        // "429 Too Many Requests". Google's read-only mirror of Central is used if that happens.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
    }
}

rootProject.name = "Testmond"
include(":app")
