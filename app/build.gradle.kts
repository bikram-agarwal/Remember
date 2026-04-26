import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }
}

// Release signing only when keystore.properties exists (CI can write it from secrets).
// Local assembleRelease stays unsigned without that file; sign locally with your own tooling if needed.
val keystoreProps = Properties()
val keystorePropsFile = rootProject.file("keystore.properties")
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}

val releaseStoreFile =
    keystoreProps.getProperty("storeFile")?.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }?.takeIf { it.isFile }
val releaseStorePassword = keystoreProps.getProperty("storePassword")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = keystoreProps.getProperty("keyAlias")?.takeIf { it.isNotBlank() }
val releaseKeyPassword =
    keystoreProps.getProperty("keyPassword")?.takeIf { it.isNotBlank() } ?: releaseStorePassword

val hasReleaseSigning =
    releaseStoreFile != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null

extensions.configure<ApplicationExtension>("android") {
    val rememberApplicationId = "dev.bikram.remember"
    namespace = rememberApplicationId
    compileSdk = 36

    defaultConfig {
        applicationId = rememberApplicationId
        minSdk = 30
        targetSdk = 36
        versionCode = 50
        versionName = "0.5.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile!!
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        debug {
        }
        create("devRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            matchingFallbacks += listOf("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            applicationIdSuffix = ".gh"
            buildConfigField("String", "GITHUB_REPO", "\"bikram-agarwal/Remember\"")
            buildConfigField("String", "PLAY_STORE_LISTING_URL", "\"https://play.google.com/store/apps/details?id=dev.bikram.remember\"")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "GITHUB_REPO", "\"bikram-agarwal/Remember\"")
            buildConfigField("String", "PLAY_STORE_LISTING_URL", "\"https://play.google.com/store/apps/details?id=dev.bikram.remember\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation("sh.calvin.reorderable:reorderable:2.4.3")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.material.kolor)
    implementation(libs.coil.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.documentfile)
    // Google account picker + OAuth token mint for Google Tasks import.
    // play-services-auth provides Identity Services (modern picker + Authorization API).
    // androidx.credentials provides clearCredentialState() which is the only reliable way to
    // force the modern picker to re-appear on "Switch account" without revoking the OAuth grant
    // (revoking the grant breaks the freshly-issued tokens that come back after re-consent).
    implementation(libs.play.services.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    // WYSIWYG Editor
    implementation("com.mohamedrejeb.richeditor:richeditor-compose:1.0.0-rc13")
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}
