plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.cyclonedx)
}

group = "com.bossincrypto.flashplay"
version = file("version.txt").readText().trim()
