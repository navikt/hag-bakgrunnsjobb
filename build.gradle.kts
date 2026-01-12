import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jmailen.kotlinter")
    id("maven-publish")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    register("printVersion") {
        println(project.version)
    }
}

java {
    withSourcesJar()
}

repositories {
    mavenCentral()
    mavenNav("*")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        mavenNav(rootProject.name)
    }
}

dependencies {
    val assertJVersion: String by project
    val exposedVersion: String by project
    val flywayVersion: String by project
    val hikariVersion: String by project
    val jacksonVersion: String by project
    val junitJupiterVersion: String by project
    val kotlinxCoroutinesVersion: String by project
    val kotlinxSerializationVersion: String by project
    val ktorVersion: String by project
    val logbackVersion: String by project
    val postgresqlVersion: String by project
    val prometheusVersion: String by project
    val slf4jVersion: String by project
    val testcontainersVersion: String by project

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
    testImplementation("com.zaxxer:HikariCP:$hikariVersion")
    testImplementation("org.assertj:assertj-core:$assertJVersion")
    testImplementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinxCoroutinesVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainersVersion")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$junitJupiterVersion")
    testRuntimeOnly("org.postgresql:postgresql:$postgresqlVersion")
}

fun RepositoryHandler.mavenNav(repo: String): MavenArtifactRepository {
    val githubPassword: String by project

    return maven {
        setUrl("https://maven.pkg.github.com/navikt/$repo")
        credentials {
            username = "x-access-token"
            password = githubPassword
        }
    }
}
