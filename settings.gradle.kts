rootProject.name = "android-chrome-tabs-exporter"

dependencyResolutionManagement {
    repositories {
        mavenCentral {
            metadataSources {
                mavenPom()
                artifact()
            }
        }
        maven("https://jitpack.io") {
            metadataSources {
                mavenPom()
                artifact()
            }
        }
    }

    resolutionStrategy {
        dependencySubstitution {
            // Substitute any attempt to resolve jna:5.15.0:jpms with jna-jpms:5.15.0
            substitute(module("net.java.dev.jna:jna:5.15.0:jpms")) with module("net.java.dev.jna:jna-jpms:5.15.0")
        }
    }
}
