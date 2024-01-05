import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.serialization") version "1.9.21"
    id("maven-publish")
}

group = "no.nav.helsearbeidsgiver"
version = "1.0.2"

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
        mavenNav("${rootProject.name}")
    }
}

dependencies {
    val assertJVersion: String by project
    val coroutinesVersion: String by project
    val flywayVersion: String by project
    val hikariVersion: String by project
    val jacksonModuleKotlinVersion: String by project
    val jacksonVersion: String by project
    val junitJupiterVersion: String by project
    val ktorVersion: String by project
    val logbackEncoderVersion: String by project
    val logback_version: String by project
    val postgresqlVersion: String by project
    val prometheusVersion: String by project

    val slf4jVersion: String by project
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonModuleKotlinVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    testImplementation("com.zaxxer:HikariCP:$hikariVersion")
    testImplementation("org.assertj:assertj-core:$assertJVersion")
    testImplementation("org.flywaydb:flyway-core:$flywayVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("org.postgresql:postgresql:$postgresqlVersion")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
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