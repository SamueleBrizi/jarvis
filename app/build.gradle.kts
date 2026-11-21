plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Il numero di versione cresce da solo a ogni compilazione su GitHub
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "it.samuele.jarvis"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.samuele.jarvis"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "2.0.$buildNumber"
        ndk { abiFilters += listOf("arm64-v8a") } // solo 64 bit: APK più leggero (Galaxy A34 = arm64)
    }

    // Firma fissa: così ogni nuova versione si installa sopra la vecchia senza disinstallare
    signingConfigs {
        create("jarvis") {
            storeFile = file("jarvis.keystore")
            storePassword = "jarvis2026"
            keyAlias = "jarvis"
            keyPassword = "jarvis2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("jarvis")
        }
        debug {
            signingConfig = signingConfigs.getByName("jarvis")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs { pickFirsts += listOf("**/libc++_shared.so") }
        resources { excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES") }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Wake word offline (openWakeWord "hey jarvis")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    // Riconoscimento vocale offline (Vosk italiano)
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    // IA locale (Gemma 3 1B / Qwen in formato .task)
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
}
