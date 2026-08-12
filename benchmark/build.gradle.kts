plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.bossincrypto.flashplay.benchmark"
    compileSdk = 37

    targetProjectPath = ":app"

    defaultConfig {
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.enabledRules"] = "BaselineProfile,Microbenchmark"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = false
            isMinifyEnabled = true
            matchingFallbacks += listOf("release")
            proguardFiles("benchmark-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.profileinstaller)
    implementation("androidx.arch.core:core-runtime:2.2.0")
    implementation("androidx.startup:startup-runtime:1.2.0")
}
