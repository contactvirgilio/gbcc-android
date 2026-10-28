// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so the kotlin-android plugin is no longer applied.
    id("com.android.application") version "9.4.0" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
