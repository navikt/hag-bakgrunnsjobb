rootProject.name = "hag-bakgrunnsjobb"

pluginManagement {
    val kotlinVersion: String by settings

    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("maven-publish")
    }
}


