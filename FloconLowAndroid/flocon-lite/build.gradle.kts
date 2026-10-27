@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.cc.floconlite"
    compileSdk = 33
    defaultConfig {
        minSdk = 21
        targetSdk = 34
    }
    compileOptions {
        targetCompatibility = JavaVersion.VERSION_11
        sourceCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.0")
    
    // OkHttp (使用主项目已有的版本)
    api("com.squareup.okhttp3:okhttp:4.10.0")
    
    // Gson (使用主项目已有的版本)
    implementation("com.google.code.gson:gson:2.9.1")
    
    // Android 基础库
    implementation("androidx.appcompat:appcompat:1.4.2")
    implementation("androidx.core:core-ktx:1.8.0")
}
