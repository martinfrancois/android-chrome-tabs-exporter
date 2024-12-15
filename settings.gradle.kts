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
}
