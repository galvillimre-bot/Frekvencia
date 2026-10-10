plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// GitHub Actions build-szám -> verziószám (helyben 1)
val buildNo = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "hu.galvill.frekvencia"
    compileSdk = 34

    defaultConfig {
        applicationId = "hu.galvill.frekvencia"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNo
        versionName = "1.0.$buildNo"
    }

    // Fix aláíró kulcs a repóban, hogy az új buildek frissítésként települjenek
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// Nincs külső függőség – csak az Android platform és a Kotlin stdlib
dependencies {}
