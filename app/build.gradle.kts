plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.darkgpt.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.darkgpt.app"
        minSdk = 29
        targetSdk = 35

        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // PDF: trích xuất văn bản từ tài liệu PDF
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
