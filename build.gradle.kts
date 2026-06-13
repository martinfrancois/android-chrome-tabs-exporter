plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.github.ben-manes.versions") version "0.52.0"
}

group = "com.github.martinfrancois"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls")
}

dependencies {
    implementation("com.google.guava:guava:33.4.8-jre")
    implementation("org.json:json:20250107") // JSON processing
    implementation("org.apache.poi:poi:5.4.1") // Apache POI for Excel
    implementation("org.apache.poi:poi-ooxml:5.4.1") // Apache POI for Excel OOXML format
    implementation("net.lingala.zip4j:zip4j:2.11.5") // Zip file processing
    implementation("org.springframework:spring-web:6.2.14") // Unescaping HTML

    // OS handling
    implementation("com.github.oshi:oshi-core:6.6.6") {
        // Exclude JNA and jna-platform from transitive dependencies that might introduce conflicts
        exclude(group = "net.java.dev.jna", module = "jna")
        exclude(group = "net.java.dev.jna", module = "jna-platform")
    }

    // Dark mode detection
    implementation("com.github.Dansoftowner:jSystemThemeDetector:3.9.1") {
        // Exclude JNA and jna-platform from transitive dependencies that might introduce conflicts
        exclude(group = "net.java.dev.jna", module = "jna")
        exclude(group = "net.java.dev.jna", module = "jna-platform")
    }

    // Explicitly add jna-jpms as a replacement for jna
    // Ensures JPMS support and avoids Gradle looking for jna:5.15.0:jpms
    implementation("net.java.dev.jna:jna-jpms:5.19.1")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.6")

    runtimeOnly("org.apache.logging.log4j:log4j-to-slf4j:2.24.3")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.jar {
    manifest.attributes["Main-Class"] = "com.github.martinfrancois.MainUi"
}

tasks.shadowJar {
    archiveFileName.set("android-chrome-tabs-exporter.jar")
}

if (hasProperty("buildScan")) {
    extensions.findByName("buildScan")?.withGroovyBuilder {
        setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
        setProperty("termsOfServiceAgree", "yes")
    }
}
