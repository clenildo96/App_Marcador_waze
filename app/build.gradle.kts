plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.marcador"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.marcador"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            storeFile = file(rootProject.file("keystore/locador-wme-release.jks"))
            storePassword = System.getenv("LOCADOR_WME_KEYSTORE_PASSWORD")
            keyAlias = "locador_wme"
            keyPassword = System.getenv("LOCADOR_WME_KEY_PASSWORD")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(rootProject.file("keystore/locador-wme-release.jks"))
            storePassword = System.getenv("LOCADOR_WME_KEYSTORE_PASSWORD")
            keyAlias = "locador_wme"
            keyPassword = System.getenv("LOCADOR_WME_KEY_PASSWORD")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(rootProject.file("keystore/locador-wme-release.jks"))
            storePassword = System.getenv("LOCADOR_WME_KEYSTORE_PASSWORD")
            keyAlias = "locador_wme"
            keyPassword = System.getenv("LOCADOR_WME_KEY_PASSWORD")
        }
    } 
    signingConfigs {
        create("release") {
            storeFile = file(rootProject.file("keystore/locador-wme-release.jks"))
            storePassword = System.getenv("LOCADOR_WME_KEYSTORE_PASSWORD")
            keyAlias = "locador_wme"
            keyPassword = System.getenv("LOCADOR_WME_KEY_PASSWORD")
        }
    } 
    signingConfigs {
        create("release") {
            storeFile = file(rootProject.file("keystore/locador-wme-release.jks"))
            storePassword = System.getenv("LOCADOR_WME_KEYSTORE_PASSWORD")
            keyAlias = "locador_wme"
            keyPassword = System.getenv("LOCADOR_WME_KEY_PASSWORD")
        }
    } 
    signingConfigs {
        create("release") {
            storeFile = file(rootProject.file("keystore/locador-wme-release.jks"))
            storePassword = System.getenv("LOCADOR_WME_KEYSTORE_PASSWORD")
            keyAlias = "locador_wme"
            keyPassword = System.getenv("LOCADOR_WME_KEY_PASSWORD")
        }
    } 
    signingConfigs {
        create("release") {
            storeFile = file(rootProject.file("keystore/locador-wme-release.jks"))
            storePassword = System.getenv("LOCADOR_WME_KEYSTORE_PASSWORD")
            keyAlias = "locador_wme"
            keyPassword = System.getenv("LOCADOR_WME_KEY_PASSWORD")
        }
    }buildTypes {
    release {
        isMinifyEnabled = false
        signingConfig = signingConfigs.getByName("release")
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}