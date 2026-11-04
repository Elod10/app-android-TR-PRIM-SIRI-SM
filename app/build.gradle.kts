import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signature : par défaut, l'APK est signé avec la clé partagée du dépôt (keystore/shared-debug.keystore)
// pour que chaque build CI puisse mettre à jour la version précédente installée.
// Pour une vraie clé de release, définir les variables d'environnement RELEASE_KEYSTORE_FILE,
// RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS et RELEASE_KEY_PASSWORD.
val releaseKeystore: String? = System.getenv("RELEASE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "fr.trprim.siri"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.trprim.siri"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/shared-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    // org.json réel pour les tests JVM (android.jar ne contient que des stubs).
    testImplementation("org.json:json:20240303")
}
