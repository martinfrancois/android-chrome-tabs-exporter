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
    implementation("org.json:json:20231013") // JSON processing
    implementation("org.apache.poi:poi:5.2.5") // Apache POI for Excel
    implementation("org.apache.poi:poi-ooxml:5.2.5") // Apache POI for Excel OOXML format
    implementation("net.lingala.zip4j:zip4j:2.11.5") // Zip file processing
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.release = 21
}

tasks.jar {
    manifest.attributes["Main-Class"] = "com.github.martinfrancois.Main"
}