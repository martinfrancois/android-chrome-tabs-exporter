plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.github.martinfrancois"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.guava:guava:33.0.0-jre")
    implementation("org.json:json:20240205") // JSON processing
    implementation("org.apache.poi:poi:5.2.5") // Apache POI for Excel
    implementation("org.apache.poi:poi-ooxml:5.2.5") // Apache POI for Excel OOXML format
    implementation("net.lingala.zip4j:zip4j:2.11.5") // Zip file processing
    implementation("com.github.oshi:oshi-core:6.4.12") // OS handling
    implementation("org.springframework:spring-web:6.1.3") // Unescaping HTML
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.25.3")
    runtimeOnly("org.apache.logging.log4j:log4j-to-slf4j:2.22.1")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.12")
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
    manifest.attributes["Main-Class"] = "com.github.martinfrancois.Main"
}

tasks.shadowJar {
    archiveFileName.set("android-chrome-tabs-exporter.jar")
}
