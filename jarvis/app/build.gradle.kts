plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jarvis.assistant"
    compileSdk = 35

    // pinned so CI and local builds resolve the exact same NDK
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.jarvis.assistant"
        minSdk = 26          // adaptive icons (mipmap-anydpi-v26), FGS types checked at runtime
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            // llama.cpp + sherpa-onnx ship these ABIs; drop any you don't need
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false   // flip on once keep-rules are battle-tested
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // web layer — the ONLY networking code is web/WebFetcher.kt (OkHttp)
    implementation(libs.okhttp)
    // Readability-style article extraction (also parses the Google News RSS)
    implementation(libs.jsoup)

    // fully-offline STT / TTS / keyword-spotting (JNI AAR from JitPack)
    implementation(libs.sherpa.onnx)
}
