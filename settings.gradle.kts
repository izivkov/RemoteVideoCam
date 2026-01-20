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
        // Add maven for GetStream WebRTC if needed, but it's on Maven Central (sonatype typically synced)
        // Check if we need explicit sonatype? No, usually Maven Central is enough for GetStream releases.
    }
}

rootProject.name = "RemoteVideoCam"
include(":app")
