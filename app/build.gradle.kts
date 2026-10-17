plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Optional stable signing key supplied by CI (GitHub Secrets) so the signing
// SHA-1 stays the same across builds. Google OAuth for Android is bound to
// package name + SHA-1, so a changing key would break sign-in.
val ciKeystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "com.sonas.actiontracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sonas.actiontracker"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (ciKeystorePath != null) {
            create("ci") {
                storeFile = file(ciKeystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (ciKeystorePath != null) signingConfig = signingConfigs.getByName("ci")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Encrypted local storage
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:sqlcipher-android:4.9.0")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Home-screen widget
    implementation("androidx.glance:glance-appwidget:1.1.1")

    // On-device Gemini Nano (ML Kit GenAI) for cleaner email suggestion titles
    implementation("com.google.mlkit:genai-summarization:1.0.0-beta1")
    // On-device Gemini Nano proofreading (voice input mode) to tidy spoken task titles
    implementation("com.google.mlkit:genai-proofreading:1.0.0-beta1")

    // Background sync
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Google sign-in / OAuth for Gmail + Calendar
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
