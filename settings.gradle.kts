pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "cube-run"
include(":app")
include(":bot")
project(":bot").projectDir = file("tools/bot")
