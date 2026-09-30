plugins {
    id("java")
    id("jacoco")
    id("com.gradleup.shadow") version "9.6.1"
    id("org.openjfx.javafxplugin") version "0.1.0"
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
    implementation("com.google.guava:guava:33.7.1-jre")
    implementation("org.json:json:20250517") // JSON processing
    implementation("org.apache.poi:poi:5.5.1") // Apache POI for Excel
    implementation("org.apache.poi:poi-ooxml:5.5.1") // Apache POI for Excel OOXML format
    implementation("net.lingala.zip4j:zip4j:2.11.6") // Zip file processing
    implementation("org.springframework:spring-web:7.0.9") // Unescaping HTML

    // OS handling.
    //
    // Pinned to the 6.x line on purpose. jSystemThemeDetector (below) links against
    // `oshi.SystemInfo.getCurrentPlatform()` from the static initialiser of
    // `com.jthemedetecor.util.OsInfo`, and that method returns `oshi.PlatformEnum`. oshi-core
    // 7.0.0 deleted both: the method is gone from `SystemInfo` and `PlatformEnum` moved to
    // `oshi.util.PlatformEnum` in the separate oshi-common artifact. Nothing in the application
    // calls the removed method itself, so the whole build still compiles against oshi-core 7.x;
    // the breakage only shows up as a `NoSuchMethodError` the first time the theme detector is
    // touched, which is every first launch on a machine with no stored theme preference.
    //
    // 6.12.0 is the newest oshi-core release that still ships `oshi/PlatformEnum.class` and
    // still declares `public static oshi.PlatformEnum getCurrentPlatform()`, so it is the
    // highest version that satisfies the detector. Raising this line requires a
    // jSystemThemeDetector release built against oshi 7.x; none exists yet (3.9.1 is the latest
    // release and still declares oshi-core 5.8.6). Adding oshi-common does not help: it carries
    // `oshi.util.PlatformEnum`, which is a different class from the `oshi.PlatformEnum` the
    // detector's call site names, and it ships no `oshi.SystemInfo` at all.
    //
    // OsThemeDetectorLaunchTest exercises the real detector so this pairing cannot regress
    // silently again.
    implementation("com.github.oshi:oshi-core:6.12.0") {
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

    // Explicitly add jna-jpms and jna-platform-jpms as replacements for jna and jna-platform.
    // Ensures JPMS support and avoids Gradle looking for jna:5.15.0:jpms. oshi-core 6.x asks for
    // the plain jna/jna-platform pair that the exclusions above strip, and it needs
    // `com.sun.jna.platform.*` at runtime, so jna-platform-jpms has to be declared here rather
    // than arrive transitively the way it did from oshi-core 7.x.
    implementation("net.java.dev.jna:jna-jpms:5.19.1")
    implementation("net.java.dev.jna:jna-platform-jpms:5.19.1")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
    // Monocle is the headless JavaFX glass backend. It lets the UI tests drive the real
    // JavaFX toolkit (real properties, real bindings, real Scene/Stage) on machines and CI
    // runners that have no X11/GTK display available.
    testImplementation("org.testfx:openjfx-monocle:21.0.2")

    runtimeOnly("org.apache.logging.log4j:log4j-to-slf4j:2.26.1")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.19")
}

tasks.test {
    useJUnitPlatform()

    // Run the JavaFX toolkit headlessly through Monocle so the UI tests work with no display.
    systemProperty("java.awt.headless", "true")
    systemProperty("glass.platform", "Monocle")
    systemProperty("monocle.platform", "Headless")
    systemProperty("prism.order", "sw")
    systemProperty("prism.verbose", "false")
    systemProperty("testfx.headless", "true")

    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }

    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(true)
    }
}

// Coverage gate. `./gradlew check` (and therefore `build`) fails when line coverage of the
// production sources drops below 80%. There are deliberately no class or package exclusions:
// every class under src/main counts, including the JavaFX UI.
val minimumLineCoverage = "0.80".toBigDecimal()

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = minimumLineCoverage
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
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
