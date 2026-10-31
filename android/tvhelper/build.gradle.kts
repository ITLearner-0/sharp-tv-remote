// Petite application d'aide installée sur la TV par ADB : liste exacte des applications
// (vrais noms, icônes) et visionneuse de photos et vidéos du téléphone, sans Google Cast.
plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.itlearner0.tvremote.helper"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.itlearner0.tvremote.helper"
        minSdk = 21
        targetSdk = 35
        versionCode = 2
        versionName = "2"
    }

    signingConfigs {
        create("test") {
            storeFile = file("../app/test-signing.keystore")
            storePassword = "android"
            keyAlias = "test"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("test")
        }
        debug {
            signingConfig = signingConfigs.getByName("test")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
