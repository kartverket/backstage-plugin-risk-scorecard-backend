plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
    id("org.springframework.boot")
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    version.set("1.6.0")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

// crypto is a library — disable the executable fat JAR, enable the plain JAR
tasks.bootJar { enabled = false }
tasks.jar { enabled = true }

val springBootVersion = "3.5.6"
val kotlinxSerializationVersion = "1.10.0"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion")) {
        because("Imports Spring Boot BOM so Jackson and other managed deps resolve without explicit versions.")
    }
    implementation("org.springframework.boot:spring-boot-starter:$springBootVersion") {
        because("Provides @ConfigurationProperties, @Service, and core Spring Boot infrastructure.")
    }
    implementation("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion") {
        because("Provides HealthIndicator used by SopsHealthIndicator.")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml") {
        because("Required by EncryptionService and Utils for SOPS YAML config I/O.")
    }
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin") {
        because("Required by EncryptionService and Utils for Jackson Kotlin extension functions.")
    }
}
