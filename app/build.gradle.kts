plugins {
    alias(libs.plugins.android.application)
}

val appVersion = rootProject.file("version.txt").readText().trim()
val versionParts = appVersion.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
val computedVersionCode =
    (versionParts.getOrElse(0) { 0 } * 1_000_000) +
        (versionParts.getOrElse(1) { 0 } * 1_000) +
        versionParts.getOrElse(2) { 0 }
val signingEnvironment = mapOf(
    "storeFile" to System.getenv("ANDROID_KEYSTORE_PATH"),
    "storePassword" to System.getenv("ANDROID_KEYSTORE_PASSWORD"),
    "keyAlias" to System.getenv("ANDROID_KEY_ALIAS"),
    "keyPassword" to System.getenv("ANDROID_KEY_PASSWORD"),
)
val hasAnySigningValue = signingEnvironment.values.any { !it.isNullOrBlank() }
val hasCompleteSigning = signingEnvironment.values.all { !it.isNullOrBlank() }
check(!hasAnySigningValue || hasCompleteSigning) {
    "Release signing requires ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD."
}

android {
    namespace = "com.bossincrypto.flashplay"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bossincrypto.flashplay"
        minSdk = 26
        targetSdk = 37
        versionCode = computedVersionCode.coerceAtLeast(1)
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    if (hasCompleteSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(signingEnvironment.getValue("storeFile")!!)
                storePassword = signingEnvironment.getValue("storePassword")
                keyAlias = signingEnvironment.getValue("keyAlias")
                keyPassword = signingEnvironment.getValue("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
        buildTypes.named("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9+ supplies Kotlin; keep compiler settings on the built-in DSL.
    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/DEPENDENCIES",
        )
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        disable += setOf("MergeRootFrame", "NewerVersionAvailable")
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
