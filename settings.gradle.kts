pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Local directory that holds the LibreOfficeKit AAR / prebuilt native libs.
        // See docs/LIBREOFFICE.md for how to populate this. It is optional; the build
        // works without it and falls back to the lightweight office viewer.
        flatDir { dirs("libs") }
    }
}

rootProject.name = "FileVault"
include(":app")
