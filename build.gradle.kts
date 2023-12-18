plugins {
    id("java")
}

group = "com.github.martinfrancois"
version = "1.0-SNAPSHOT"

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
}

tasks.test {
    useJUnitPlatform()
}