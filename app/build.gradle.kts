import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Resolves the short git commit hash at configuration time so it can be baked
// into BuildConfig. Falls back to "unknown" if git isn't available (shouldn't
// happen in CI, but keeps local builds from failing if .git is missing for
// some reason -- e.g. a zip export without history).
val gitCommitHash: String = try {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        standardOutput = stdout
    }
    stdout.toString().trim()
} catch (e: Exception) {
    "unknown"
}

android {
    namespace = "com.evyr.rebuild"
    // Compile-time only -- minSdk 26 and targetSdk 34 are unchanged, so device
    // compatibility and runtime behavior are unaffected.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.evyr.rebuild"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GIT_COMMIT_HASH", "\"$gitCommitHash\"")
        buildConfigField("long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
    }

    signingConfigs {
        getByName("debug") {
            // Without this, AGP auto-generates a fresh debug keystore inside each
            // GitHub Actions runner and discards it when the job ends -- every
            // build gets a different signature, and Android refuses to install an
            // "update" over an app signed with a different key. That forced a
            // clean install every single time. Pointing at a fixed, committed
            // keystore makes every CI build sign identically, so updates install
            // over the existing app normally.
            storeFile = file("${rootProject.projectDir}/keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Encrypted prefs for PIN storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Location / geofencing
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // WorkManager — powers the delayed arrival check and notification snooze
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.biometric:biometric:1.1.0")

    // Kept even though CameraX (its original reason) is gone: WorkManager also
    // exposes ListenableFuture, and Guava publishes an intentionally-empty
    // listenablefuture stub that Gradle prefers whenever full Guava is in the
    // graph. Without this declared, that stub wins and the real class vanishes.
    implementation("com.google.guava:guava:33.7.1-android")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Image loading for local file paths (media attachments)
    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
