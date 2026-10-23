pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}
rootProject.name = "AncientRPG"
include("protocol", "client-mod")
