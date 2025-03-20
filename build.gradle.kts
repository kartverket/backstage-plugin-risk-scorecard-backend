import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.4.0"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    kotlin("plugin.serialization") version "2.0.21"
}

group = "no"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://dist.wso2.org/maven2/public")
    }
}

val kotlinVersion = "2.0.21"
val springBootVersion = "3.4.1"
val springSecurityVersion = "6.4.1"
val kotlinxSerializationVersion = "1.7.3"
val kotlinxCoroutinesVersion = "1.9.0"
val nettyVersion = "4.1.115.Final"
val micrometerVersion = "1.14.1"
val fasterXmlJacksonVersion = "2.18.2"
val kotlinJsonSchemaVersion = "0.48"
val apacheCommonsVersion = "3.17.0"
val googleGsonVersion = "2.11.0"
val googleAuthVersion = "1.30.0"
val googleGuavaVersion = "33.3.1-jre"
val nimbusdsVersion = "9.47"
val bouncyCastleVersion = "1.79"
val jsonWebTokenVersion = "0.12.6"
val ninjaSquadVersion = "4.0.2"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.apache.tomcat.embed:tomcat-embed-core") {
        version {
            strictly("10.1.34")
        }
    }
    implementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-security:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-webflux:$springBootVersion")
    implementation("org.springframework.security:spring-security-oauth2-jose:$springSecurityVersion")
    implementation("org.springframework.security:spring-security-oauth2-resource-server:$springSecurityVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    implementation("io.netty:netty-all:$nettyVersion")

    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$fasterXmlJacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$fasterXmlJacksonVersion")
    implementation("net.pwall.json:json-kotlin-schema:$kotlinJsonSchemaVersion")
    implementation("org.apache.commons:commons-lang3:$apacheCommonsVersion")

    implementation("com.google.code.gson:gson:$googleGsonVersion")
    implementation("com.google.auth:google-auth-library-credentials:$googleAuthVersion")
    implementation("com.google.guava:guava:$googleGuavaVersion")

    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion")
    implementation("org.bouncycastle:bcpkix-jdk18on:$bouncyCastleVersion")

    implementation("io.jsonwebtoken:jjwt-api:$jsonWebTokenVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jsonWebTokenVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jsonWebTokenVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("com.ninja-squad:springmockk:$ninjaSquadVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinxSerializationVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
