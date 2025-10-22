@file:Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    // configures repositories for all projects
    repositories {
        mavenLocal()
        mavenCentral()

        maven {
            name = "OSS Sonatype Snapshots"
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
        maven { url = uri("https://jitpack.io") }
    }

    // only use these repos
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

rootProject.name = "Playtime"
include("core")
include("spigot")
include("bungee")
