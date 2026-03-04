plugins {
    val kotlinPluginsVersion = "2.3.0"
    kotlin("jvm") version kotlinPluginsVersion apply false
    kotlin("plugin.spring") version kotlinPluginsVersion apply false
    kotlin("plugin.serialization") version kotlinPluginsVersion apply false
    id("org.springframework.boot") version "4.0.3" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1" apply false
}

subprojects {
    group = "no"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
