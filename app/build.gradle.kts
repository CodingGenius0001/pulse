import java.util.Properties
import org.gradle.api.tasks.Copy

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

/**
 * Build number used by the in-app updater to compare versions. Locally this
 * is just 0 (no GitHub Actions context). On CI it's set via the
 * GITHUB_RUN_NUMBER env var so the APK knows what build it is, and the
 * updater can compare to the latest release's build number.
 */
val ciBuildNumber: Int = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toIntOrNull() ?: 0

/**
 * Path to a debug keystore we own (instead of the random one Gradle generates
 * per machine). When it exists, all debug builds are signed with it — meaning
 * builds installed by users can be upgraded by future builds without needing
 * to uninstall first.
 *
 * On CI the keystore file is created at build time from a base64 secret;
 * locally you can drop your own at app/keystore/debug.jks.
 */
val debugKeystoreFile = file("keystore/debug.jks")

android {
    namespace = "com.pulse.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pulse.music"
        minSdk = 26
        targetSdk = 34
        versionCode = 17
        versionName = "0.5.10"

        vectorDrawables {
            useSupportLibrary = true
        }

        // Exposed to Kotlin via BuildConfig.* — see network/GeniusApi.kt and
        // update/UpdateRepository.kt for consumers.
        buildConfigField("String", "GENIUS_ACCESS_TOKEN", "\"$geniusToken\"")
        buildConfigField("int", "BUILD_NUMBER", "$ciBuildNumber")
        buildConfigField("String", "GITHUB_REPO", "\"CodingGenius0001/pulse\"")
        buildConfigField("String", "RELEASE_TAG", "\"latest\"")
    }

    signingConfigs {
        // We define a custom config only when the keystore actually exists.
        // First-time local builds (no keystore) fall through to Gradle's
        // auto-generated debug keystore, which works fine — just doesn't
        // give the cross-build install compatibility we want for CI.
        if (debugKeystoreFile.exists()) {
            create("pulseDebug") {
                storeFile = debugKeystoreFile
                storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD")
                    ?: "pulseDebugStorePass"
                keyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "pulseDebugKey"
                keyPassword = System.getenv("DEBUG_KEY_PASSWORD")
                    ?: "pulseDebugKeyPass"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            // Apply the custom debug signing config when available.
            if (debugKeystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("pulseDebug")
            }
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
    sourceSets {
        getByName("main") {
            assets.srcDir(layout.buildDirectory.dir("generated/pulseAssets"))
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val syncReadmeToAssets = tasks.register<Copy>("syncReadmeToAssets") {
    from(rootProject.file("README.md"))
    into(layout.buildDirectory.dir("generated/pulseAssets"))
    rename { "README.md" }
}

tasks.named("preBuild") {
    dependsOn(syncReadmeToAssets)
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
