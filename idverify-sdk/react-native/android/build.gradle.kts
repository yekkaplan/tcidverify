plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.idverify.reactnative"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    // React Native
    implementation("com.facebook.react:react-android")
    
    // IDVerify Core SDK
    implementation(project(":idverify-sdk:android"))
    
    // CameraX (required by IDVerify SDK)
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    
    // ML Kit (required by IDVerify SDK)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    
    // Coroutines (required by IDVerify SDK)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
