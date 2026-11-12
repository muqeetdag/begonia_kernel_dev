/*
 * SablePureBlack — by @drkmuqeett
 * App module build script (Kotlin DSL)
 *
 * Releases are R8-minified and resource-shrunk; `proguard-rules.pro` pins the
 * Xposed entry point named in assets/xposed_init so shrinking can never break
 * hook loading.
 */

import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * Optional upload key. When `signing.properties` is absent the build falls back
 * to the standard Android debug keystore, which `ensureFallbackKeystore`
 * generates on a pristine machine — so `assembleRelease` always emits a signed,
 * installable APK with zero manual setup.
 */
val signingPropsFile = rootProject.file("signing.properties")
val signingProps = Properties().apply {
    if (signingPropsFile.exists()) {
        signingPropsFile.inputStream().use { load(it) }
    }
}

val hasUploadKey: Boolean = signingProps.getProperty("storeFile") != null
val androidHome = File(System.getProperty("user.home"), ".android")
val fallbackKeystore = File(androidHome, "debug.keystore")

val ensureFallbackKeystore = tasks.register("ensureFallbackKeystore") {
    outputs.file(fallbackKeystore)
    doLast {
        if (fallbackKeystore.exists()) {
            return@doLast
        }
        androidHome.mkdirs()
        val keytool = File(System.getProperty("java.home"), "bin/keytool")
        val execResult = providers.exec {
            commandLine(
                keytool.absolutePath, "-genkeypair", "-v",
                "-keystore", fallbackKeystore.absolutePath,
                "-storepass", "android",
                "-alias", "androiddebugkey",
                "-keypass", "android",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "10000",
                "-dname", "CN=SablePureBlack Debug,O=drkmuqeett,C=US"
            )
            isIgnoreExitValue = true
        }.result.get()
        if (execResult.exitValue == 0) {
            logger.lifecycle("SablePureBlack: generated fallback keystore at ${fallbackKeystore.absolutePath}")
        } else {
            logger.warn("SablePureBlack: keytool failed — reusing any existing debug keystore")
        }
    }
}

tasks.named("preBuild") { dependsOn(ensureFallbackKeystore) }

android {
    namespace = "com.drkmuqeett.pureblack"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.drkmuqeett.pureblack"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"
    }

    signingConfigs {
        if (hasUploadKey) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
        create("fallback") {
            storeFile = fallbackKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasUploadKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("fallback")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module",
                "kotlin/**",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }
}

dependencies {
    // Xposed framework API — provided at runtime by LSPosed / Zygisk Vector.
    compileOnly("de.robv.android.xposed:api:82")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.annotation:annotation:1.7.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.11.0")
    // Genuine spring physics for the UI (SpringAnimation / SpringForce).
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    // Lifecycle-aware coroutine scope for the app-list scan.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
