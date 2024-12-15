rootProject.name = "android-chrome-tabs-exporter"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral {
            // Use only Maven POM and artifact metadata, exclude Gradle Module Metadata
            metadataSources {
                mavenPom()
                artifact()
                // Exclude gradleMetadata() to prevent incorrect variant selection
            }
        }
        maven("https://jitpack.io") {
            metadataSources {
                mavenPom()
                artifact()
            }
        }
    }

    components {
        withModule("net.java.dev.jna:jna") {
            allVariants {
                withCapabilities {
                    // Remove any capabilities that mention JPMS to prevent Gradle from seeking jpms classifiers
                    removeAll { capability ->
                        capability.name.contains("jpms", ignoreCase = true)
                    }
                }
            }
        }
    }
}
