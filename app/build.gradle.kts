import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(
        libs.versions.java
            .get()
            .toInt(),
    )
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
        providers
            .gradleProperty("kotlin.compiler.metrics.destination")
            .orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { metricsDestination ->
                freeCompilerArgs.addAll(
                    "-P",
                    "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$metricsDestination",
                    "-P",
                    "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$metricsDestination",
                )
            }
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
    keystoreProps
        .getProperty("storeFile")
        ?.takeIf { it.isNotBlank() }
        ?.let { rootProject.file(it) }
        ?.takeIf { it.isFile }
val releaseStorePassword = keystoreProps.getProperty("storePassword")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = keystoreProps.getProperty("keyAlias")?.takeIf { it.isNotBlank() }
val releaseKeyPassword = keystoreProps.getProperty("keyPassword")?.takeIf { it.isNotBlank() }

val hasReleaseSigning =
    releaseStoreFile != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null

extensions.configure<ApplicationExtension>("android") {
    val rememberApplicationId = "dev.bikram.remember"
    namespace = rememberApplicationId
    compileSdk = 37

    defaultConfig {
        applicationId = rememberApplicationId
        minSdk = 31
        targetSdk = 37
        versionCode = 80
        versionName = "0.8.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    lint {
        baseline = file("lint-baseline.xml")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            applicationIdSuffix = ".gh"
            buildConfigField("String", "GITHUB_REPO", "\"bikram-agarwal/Remember\"")
            buildConfigField("String", "PLAY_STORE_LISTING_URL", "\"https://play.google.com/store/apps/details?id=dev.bikram.remember\"")
            buildConfigField("Boolean", "SHOW_UPDATES", "true")
            buildConfigField("Boolean", "USE_PLAY_IN_APP_UPDATES", "false")
            buildConfigField("String", "CHANGELOG_GITHUB_REPO", "\"bikram-agarwal/Remember\"")
            buildConfigField("String", "CHANGELOG_GITHUB_BRANCH", "\"main\"")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "GITHUB_REPO", "\"bikram-agarwal/Remember\"")
            buildConfigField("String", "PLAY_STORE_LISTING_URL", "\"https://play.google.com/store/apps/details?id=dev.bikram.remember\"")
            buildConfigField("Boolean", "SHOW_UPDATES", "true")
            buildConfigField("Boolean", "USE_PLAY_IN_APP_UPDATES", "true")
            buildConfigField("String", "CHANGELOG_GITHUB_REPO", "\"bikram-agarwal/Remember\"")
            buildConfigField("String", "CHANGELOG_GITHUB_BRANCH", "\"main\"")
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

    sourceSets {
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

ktlint {
    android.set(true)
    version.set("1.8.0")
}

configurations.named("detekt") {
    resolutionStrategy {
        force(
            "io.github.detekt.sarif4k:sarif4k:0.7.0",
            "io.github.detekt.sarif4k:sarif4k-jvm:0.7.0",
            "io.github.oshai:kotlin-logging:8.0.03",
        )
    }
}

dependencies {
    implementation(libs.reorderable)
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
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.material.kolor)
    implementation(libs.coil.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.documentfile)
    // Google account picker + OAuth token mint for Google Tasks import.
    // play-services-auth provides Identity Services (modern picker + Authorization API).
    // androidx.credentials provides clearCredentialState() for explicit Disconnect cleanup.
    implementation(libs.play.services.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.profileinstaller)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    add("playstoreImplementation", "com.google.android.play:app-update:2.1.0")
    add("playstoreImplementation", "com.google.android.play:app-update-ktx:2.1.0")
    add("playstoreImplementation", "com.google.android.play:review:2.0.2")
    add("playstoreImplementation", "com.google.android.play:review-ktx:2.0.2")
}
