import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Loads the Genius API token from local.properties (gitignored) or the
 * GENIUS_ACCESS_TOKEN env var (for CI). Falls back to an empty string so
 * builds succeed even without a token — the app just won't fetch metadata
 * online in that case.
 */
val geniusToken: String = run {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { props.load(it) }
    }
    props.getProperty("GENIUS_ACCESS_TOKEN")
        ?: System.getenv("GENIUS_ACCESS_TOKEN")
        ?: ""
}

android {
    namespace = "com.pulse.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pulse.music"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "0.4.1"

        vectorDrawables {
            useSupportLibrary = true
        }

        // Expose the Genius token to Kotlin via BuildConfig.GENIUS_ACCESS_TOKEN
        buildConfigField("String", "GENIUS_ACCESS_TOKEN", "\"$geniusToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
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
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Coil image loading
    implementation(libs.coil.compose)

    // DataStore preferences
    implementation(libs.androidx.datastore.preferences)

    // Palette for album art color extraction
    implementation(libs.androidx.palette.ktx)

    // Runtime permissions
    implementation(libs.accompanist.permissions)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)
}
