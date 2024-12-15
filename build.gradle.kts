plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.github.ben-manes.versions") version "0.51.0"
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
    implementation("com.google.guava:guava:33.3.1-jre")
    implementation("org.json:json:20240303") // JSON processing
    implementation("org.apache.poi:poi:5.3.0") // Apache POI for Excel
    implementation("org.apache.poi:poi-ooxml:5.3.0") // Apache POI for Excel OOXML format
    implementation("net.lingala.zip4j:zip4j:2.11.5") // Zip file processing
    implementation("org.springframework:spring-web:6.2.1") // Unescaping HTML
    implementation("com.github.oshi:oshi-core:6.6.5") // OS handling
    implementation("com.github.Dansoftowner:jSystemThemeDetector:3.9.1") // Dark mode detection

    // Explicitly include standard JNA and jna-jpms
    implementation("net.java.dev.jna:jna:5.15.0")
    implementation("net.java.dev.jna:jna-jpms:5.15.0")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.26.3")
    runtimeOnly("org.apache.logging.log4j:log4j-to-slf4j:2.24.2")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
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

// Resolution Strategy to Force Standard JNA and jna-platform Artifacts
configurations.all {
    resolutionStrategy {
        eachDependency {
            when {
                requested.group == "net.java.dev.jna" && requested.name == "jna" -> {
                    useVersion("5.15.0")
                }
                requested.group == "net.java.dev.jna" && requested.name == "jna-platform" -> {
                    useVersion("5.15.0")
                }
            }
        }

        // Force specific versions to ensure consistency across all dependencies
        force("net.java.dev.jna:jna:5.15.0")
        force("net.java.dev.jna:jna-platform:5.15.0")
    }
}
