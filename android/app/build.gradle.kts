plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.itlearner0.tvremote"
    compileSdk = 35

    defaultConfig {
        // Identifiant définitif (Play Store) ; le code garde son nom de paquet d'origine.
        applicationId = "app.omnizap.remote"
        minSdk = 26
        targetSdk = 35
        // Numéro de version fourni par GitHub Actions pour que chaque APK remplace le précédent.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "0.1.${System.getenv("VERSION_CODE") ?: "0"}"
        // Versions de test (hors Play Store) : bouton de déblocage de Pro pour les essais.
        // À la publication, compiler avec PLAY_RELEASE=1 pour le retirer.
        buildConfigField("boolean", "TEST_UNLOCK", (System.getenv("PLAY_RELEASE") == null).toString())
    }

    signingConfigs {
        // Clé de TEST, publique : permet d'installer chaque nouvelle version par-dessus
        // la précédente. À remplacer par une clé privée avant toute publication.
        create("test") {
            storeFile = file("test-signing.keystore")
            storePassword = "android"
            keyAlias = "test"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // R8 retire le code inutilisé (notamment les icônes non utilisées).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/previous-compilation-data.bin")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("dev.mobile:dadb:1.2.10")
    // WebSocket des TV Samsung (Tizen) et LG (webOS).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Google Cast : affichage des photos et vidéos du téléphone sur la TV.
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    // Google Play : achat unique d'OmniZap Pro.
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    testImplementation("junit:junit:4.13.2")
}
