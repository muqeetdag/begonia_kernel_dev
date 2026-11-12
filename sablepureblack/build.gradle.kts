/*
 * SablePureBlack — by @drkmuqeett
 * Root build script (Kotlin DSL)
 *
 * Pinned toolchain: AGP 8.2.2 + Kotlin 1.9.22 + Gradle 8.2 + JDK 17.
 * Deliberately no Compose / KSP / annotation processors in the plugin block:
 * `./gradlew assembleRelease` stays a single, dependency-light, deterministic pass.
 */

plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
