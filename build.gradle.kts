
plugins {
    id("java")
    id("jacoco")
    id("distribution")
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
    implementation("com.google.guava:guava:33.7.2-jre")
    implementation("org.json:json:20250517") // JSON processing
    implementation("org.apache.poi:poi:6.0.0") // Apache POI for Excel
    implementation("org.apache.poi:poi-ooxml:6.0.0") // Apache POI for Excel OOXML format
    implementation("net.lingala.zip4j:zip4j:2.11.6") // Zip file processing
    implementation("org.springframework:spring-web:7.0.10") // Unescaping HTML

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
    runtimeOnly("org.slf4j:slf4j-simple:2.0.20")
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
    manifest.attributes["Main-Class"] = "com.github.martinfrancois.Launcher"
}

tasks.processResources {
    from(listOf("LICENSE", "NOTICE.md", "licenses/Apache-2.0.txt")) {
        // Keep project and font notices separate from bundled dependencies' notices.
        into("META-INF/android-chrome-tabs-exporter")
    }
}

tasks.shadowJar {
    archiveFileName.set("android-chrome-tabs-exporter.jar")
    // JavaFX must retain its named modules rather than enter the shaded classpath.
    dependencies {
        exclude(dependency("org.openjfx:.*:.*"))
    }
    // Shadow merges Kotlin module metadata with a transformer, and the default EXCLUDE strategy
    // would drop a second file of the same name before the transformer sees it.
    filesMatching("META-INF/*.kotlin_module") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    // Most bundled Apache projects ship their notice under the same two names, and section 4(d)
    // of the Apache License asks a redistribution to keep each one. poi and poi-ooxml also both
    // register a WorkbookProvider. Every copy is merged, so none is dropped as a duplicate.
    val notices = listOf("META-INF/NOTICE", "META-INF/NOTICE.txt")
    notices.forEach { append(it) }
    mergeServiceFiles()
    filesMatching(notices + "META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

val javafxRuntime = configurations.runtimeClasspath.get().incoming.artifactView {
    componentFilter {
        it is org.gradle.api.artifacts.component.ModuleComponentIdentifier && it.group == "org.openjfx"
    }
}.files

// The files every packaging ships next to JavaFX: the application JAR and the notices.
val applicationFiles = copySpec {
    from(tasks.shadowJar) { into("lib") }
    from("LICENSE", "NOTICE.md")
    from("licenses") { into("licenses") }
}

distributions {
    main {
        contents {
            with(applicationFiles)
            from(javafxRuntime) { into("modules") }
            filesMatching("bin/*") { permissions { unix("0755") } }
        }
    }
}

tasks.distZip {
    archiveFileName.set("android-chrome-tabs-exporter-linux.zip")
}

// Native installers. jpackage from the Java 21 toolchain bundles a Java runtime into each one, so
// users need no Java installation. -PinstallerType picks the format the build host can make:
// app-image, deb or rpm on Linux, msi on Windows, dmg on macOS. -PinstallerVersion sets the
// version the installer records; Windows only upgrades over an installed copy with a lower one.
val installerType = providers.gradleProperty("installerType").orElse("app-image")
val installerVersion = providers.gradleProperty("installerVersion").orElse("1.0.0")
val hostOs = System.getProperty("os.name").lowercase()
val hostIsWindows = hostOs.startsWith("windows")
val hostIsMac = hostOs.startsWith("mac")
val hostIsLinux = hostOs.startsWith("linux")
val jpackageExecutable = javaToolchains.launcherFor(java.toolchain).map { launcher ->
    val name = if (hostIsWindows) "jpackage.exe" else "jpackage"
    launcher.metadata.installationPath.file("bin/$name").asFile.absolutePath
}
val jpackageInputDirectory = layout.buildDirectory.dir("jpackage/input")
val jpackageModulesDirectory = layout.buildDirectory.dir("jpackage/modules/modules")

// jpackage puts every JAR under --input on the class path, so JavaFX goes in through --app-content
// and reaches the application only as named modules, like in the ZIP.
val jpackageInput = tasks.register<Sync>("jpackageInput") {
    into(jpackageInputDirectory)
    with(applicationFiles)
}

val jpackageModules = tasks.register<Sync>("jpackageModules") {
    into(jpackageModulesDirectory)
    from(javafxRuntime)
}

tasks.register<Exec>("jpackage") {
    description = "Builds a native installer with a bundled Java runtime. -PinstallerType=app-image|deb|rpm|msi|dmg"
    group = "distribution"
    dependsOn(jpackageInput, jpackageModules)
    val input = jpackageInputDirectory
    val modules = jpackageModulesDirectory
    // One directory per type, so building a deb and then an rpm keeps both.
    val output = installerType.flatMap { layout.buildDirectory.dir("jpackage/$it") }
    inputs.dir(input)
    inputs.dir(modules)
    inputs.property("installerType", installerType)
    inputs.property("installerVersion", installerVersion)
    outputs.dir(output)
    doFirst {
        // jpackage refuses to write an app image into a directory that already holds one.
        output.get().asFile.deleteRecursively()
        executable(jpackageExecutable.get())
    }
    argumentProviders.add(CommandLineArgumentProvider {
        // Linux package and directory names stay lowercase; Windows and macOS show the name to users.
        val name = if (hostIsLinux) "android-chrome-tabs-exporter" else "Android Chrome Tabs Exporter"
        val arguments = mutableListOf(
            "--type", installerType.get(),
            "--name", name,
            "--app-version", installerVersion.get(),
            "--vendor", "François Martin",
            "--description", "Exports every open tab of Chrome on an Android phone into an Excel file",
            "--copyright", "Copyright François Martin",
            "--input", input.get().asFile.absolutePath,
            "--app-content", modules.get().asFile.absolutePath,
            "--main-jar", "lib/android-chrome-tabs-exporter.jar",
            "--main-class", "com.github.martinfrancois.Launcher",
            // The same module setup as the ZIP's launcher: JavaFX from the shipped modules directory.
            "--java-options", "--module-path=\$APPDIR/../modules",
            "--java-options", "--add-modules=javafx.controls",
            "--dest", output.get().asFile.absolutePath,
        )
        // Installer options are rejected for an app image, which is only the application directory.
        if (installerType.get() != "app-image") {
            when {
                // The Linux packages install the license as their copyright file. The .msi and .dmg
                // get none: there it becomes a license agreement to accept, which MIT does not need.
                hostIsLinux -> arguments += listOf(
                    "--license-file", file("LICENSE").absolutePath,
                    "--linux-shortcut",
                    "--linux-menu-group", "Utility",
                    "--linux-deb-maintainer", "f.martin@fastmail.com",
                    "--linux-rpm-license-type", "MIT",
                    // JavaFX loads GTK and libXtst from native libraries it unpacks at startup, so
                    // the package tools cannot find these dependencies by scanning the package.
                    "--linux-package-deps",
                    if (installerType.get() == "rpm") "gtk3, libXtst" else "libgtk-3-0t64, libxtst6",
                )
                hostIsWindows -> arguments += listOf(
                    "--win-menu",
                    "--win-shortcut",
                    "--win-dir-chooser",
                    // A fixed upgrade code makes a newer installer replace an installed older version.
                    "--win-upgrade-uuid", "6f0c9f5e-2c8e-4a43-9a8d-6c3b1f7a2e41",
                )
            }
        }
        if (hostIsMac) {
            arguments += listOf("--mac-package-identifier", "com.github.martinfrancois.android-chrome-tabs-exporter")
        }
        arguments
    })
}
