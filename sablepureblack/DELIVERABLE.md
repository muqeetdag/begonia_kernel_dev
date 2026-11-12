# SablePureBlack — complete source, by @drkmuqeett

Everything below is the exact, final content of the shipped project as committed at
`sablepureblack/` (commit `6fe6f28db` on `arena/b3ca38a6-begonia-kernel-dev`).
65 files, 4,871 lines of Kotlin/XML/docs, plus the Gradle wrapper jar and a UI mockup (binary, not
inlined — see the note at the end).

* **Package** `com.drkmuqeett.pureblack` · minSdk 26 · targetSdk 34 · Java 17
* **Frameworks** Xposed API 82 (`compileOnly`) · LSPosed / Zygisk Vector / EdXposed
* **Toolchain** Gradle 8.2 · AGP 8.2.2 · Kotlin 1.9.22 · `compileSdk 34`
* **Payload entry point** `com.drkmuqeett.pureblack.core.PureBlackHook` (from `assets/xposed_init`)

Read in this order if you only want the essentials:
`app/src/main/java/com/drkmuqeett/pureblack/core/PureBlackHook.kt` (all hook logic),
`config/SableSettings.kt` + `config/PaletteMath.kt` (decision rules),
`MainActivity.kt` + `ui/SableAdapter.kt` (companion UI).

---

## File tree

```
sablepureblack/
├── .gitignore
├── README.md
├── build.gradle.kts
├── docs/
│   ├── HOOK_BEHAVIOUR.md
│   └── ui-preview.png
├── gradle.properties
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradlew
├── settings.gradle.kts
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/
        │   └── xposed_init
        ├── java/com/drkmuqeett/pureblack/
        │   ├── MainActivity.kt
        │   ├── SableApplication.kt
        │   ├── apps/
        │   │   ├── AppRepository.kt
        │   │   └── ScopeStore.kt
        │   ├── config/
        │   │   ├── PaletteMath.kt
        │   │   ├── PureBlackPrefs.kt
        │   │   └── SableSettings.kt
        │   ├── core/
        │   │   ├── Locales.kt
        │   │   ├── Probe.kt
        │   │   └── PureBlackHook.kt
        │   └── ui/
        │       ├── AppScopeAdapter.kt
        │       ├── SableAdapter.kt
        │       ├── ScopeSheet.kt
        │       ├── Springs.kt
        │       └── UiState.kt
        └── res/
            ├── drawable/ (15 bg_*/ic_launcher files)
            ├── layout/ (12 layouts)
            ├── mipmap-anydpi-v26|v33/ (4 adaptive icons)
            ├── values/ (arrays, colors, dimens, strings, themes)
            └── xml/ (backup + data-extraction rules)
```
---

## Build

```bash
./gradlew :app:assembleRelease     # -> app/build/outputs/apk/release/app-release.apk
```

Requires JDK 17 and Android SDK platform 34 (`ANDROID_HOME` + `local.properties` with
`sdk.dir=/path/to/Android/Sdk`). Signing uses `signing.properties` if present, otherwise an
auto-generated debug keystore, so `assembleRelease` always produces an installable APK.

---
### `settings.gradle.kts`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Gradle settings (Kotlin DSL)
 *
 * Root project name and module list for the SablePureBlack LSPosed module.
 */

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Xposed API 82 — compileOnly framework classes, never packaged.
        maven { url = uri("https://api.xposed.info/") }
    }
}

rootProject.name = "SablePureBlack"
include(":app")
```

---
### `build.gradle.kts`

```kotlin
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
```

---
### `gradle.properties`

```properties
# SablePureBlack — by @drkmuqeett
org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
android.nonFinalResIds=false
kotlin.code.style=official
kotlin.incremental=true
```

---
### `gradlew`

```text
#!/bin/sh

#
# Copyright © 2015 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
#

##############################################################################
#
#   gradlew start up script for POSIX generated by Gradle.
#
#   Important for running:
#
#   (1) You need a POSIX-compliant shell to run this script. If your /bin/sh is
#       noncompliant, but you have some other compliant shell such as ksh or
#       bash, then to run this script, type that shell name before the whole
#       command line, like:
#
#           ksh gradlew
#
#       Busybox and similar reduced shells will NOT work, because this script
#       requires all of these POSIX shell features:
#         * functions;
#         * expansions «$var», «${var}», «${var:-default}», «${var+SET}»,
#           «${var#prefix}», «${var%suffix}», and «$( cmd )»;
#         * compound commands having a testable exit status, especially «case»;
#         * various built-in commands including «command», «set», and «ulimit».
#
#   Important for patching:
#
#   (2) This script targets any POSIX shell, so it avoids extensions provided
#       by Bash, Ksh, etc; in particular arrays are avoided.
#
#       The "traditional" practice of packing multiple parameters into a
#       space-separated string is a well documented source of bugs and security
#       problems, so this is (mostly) avoided, by progressively accumulating
#       options in "$@", and eventually passing that to Java.
#
#       Where the inherited environment variables (DEFAULT_JVM_OPTS, JAVA_OPTS,
#       and GRADLE_OPTS) rely on word-splitting, this is performed explicitly;
#       see the in-line comments for details.
#
#       There are tweaks for specific operating systems such as AIX, CygWin,
#       Darwin, MinGW, and NonStop.
#
#   (3) This script is generated from the Groovy template
#       https://github.com/gradle/gradle/blob/3d91ce3b8caaf77ad09f381f43615b715b53f72c/platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt
#       within the Gradle project.
#
#       You can find Gradle at https://github.com/gradle/gradle/.
#
##############################################################################

# Attempt to set APP_HOME

# Resolve links: $0 may be a link
app_path=$0

# Need this for daisy-chained symlinks.
while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

# This is normally unused
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
# Discard cd standard output in case $CDPATH is set (https://github.com/gradle/gradle/issues/25036)
APP_HOME=$( cd -P "${APP_HOME:-./}" > /dev/null && printf '%s\n' "$PWD" ) || exit

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in                #(
  CYGWIN* )         cygwin=true  ;; #(
  Darwin* )         darwin=true  ;; #(
  MSYS* | MINGW* )  msys=true    ;; #(
  NONSTOP* )        nonstop=true ;;
esac



# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1
    then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
fi

# Increase the maximum file descriptors if we can.
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in #(
      max*)
        # In POSIX sh, ulimit -H is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        MAX_FD=$( ulimit -H -n ) ||
            warn "Could not query maximum file descriptor limit"
    esac
    case $MAX_FD in  #(
      '' | soft) :;; #(
      *)
        # In POSIX sh, ulimit -n is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        ulimit -n "$MAX_FD" ||
            warn "Could not set maximum file descriptor limit to $MAX_FD"
    esac
fi

# Collect all arguments for the java command, stacking in reverse order:
#   * args from the command line
#   * the main class name
#   * -classpath
#   * -D...appname settings
#   * --module-path (only if needed)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS environment variables.

# For Cygwin or MSYS, switch paths to Windows format before running java
if "$cygwin" || "$msys" ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )

    JAVACMD=$( cygpath --unix "$JAVACMD" )

    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    for arg do
        if
            case $arg in                                #(
              -*)   false ;;                            # don't mess with options #(
              /?*)  t=${arg#/} t=/${t%%/*}              # looks like a POSIX filepath
                    [ -e "$t" ] ;;                      #(
              *)    false ;;
            esac
        then
            arg=$( cygpath --path --ignore --mixed "$arg" )
        fi
        # Roll the args list around exactly as many times as the number of
        # args, so each arg winds up back in the position where it started, but
        # possibly modified.
        #
        # NB: a `for` loop captures its iteration list before it begins, so
        # changing the positional parameters here affects neither the number of
        # iterations, nor the values presented in `arg`.
        shift                   # remove old arg
        set -- "$@" "$arg"      # push replacement arg
    done
fi


# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Collect all arguments for the java command:
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and optsEnvironmentVar are not allowed to contain shell fragments,
#     and any embedded shellness will be escaped.
#   * For example: A user cannot expect ${Hostname} to be expanded, as it is an environment variable and will be
#     treated as '${Hostname}' itself on the command line.

set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
        "$@"

# Stop when "xargs" is not available.
if ! command -v xargs >/dev/null 2>&1
then
    die "xargs is not available"
fi

# Use "xargs" to parse quoted args.
#
# With -n1 it outputs one arg per line, with the quotes and backslashes removed.
#
# In Bash we could simply go:
#
#   readarray ARGS < <( xargs -n1 <<<"$var" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# but POSIX shell has neither arrays nor command substitution, so instead we
# post-process each arg (as a line of input to sed) to backslash-escape any
# character that might be a shell metacharacter, then use eval to reverse
# that process (while maintaining the separation between arguments), and wrap
# the whole thing up as a single "set" statement.
#
# This will of course break if any of these variables contains a newline or
# an unmatched quote.
#

eval "set -- $(
        printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"
```

---
### `gradle/wrapper/gradle-wrapper.properties`

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

---
### `.gitignore`

```bash
# Gradle / Android
.gradle/
build/
local.properties
captures/
.externalNativeBuild/
.cxx/

# Signing material is never committed
signing.properties
*.jks
*.keystore

# IDE
.idea/
*.iml
.DS_Store
*.hprof
```

---
### `app/build.gradle.kts`

```kotlin
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
```

---
### `app/proguard-rules.pro`

```proguard
# SablePureBlack — by @drkmuqeett
# R8 / ProGuard rules for the Xposed payload.

# The entry point is loaded reflectively from assets/xposed_init — never rename,
# shrink or remove it, or the framework silently skips the module.
-keep class com.drkmuqeett.pureblack.core.PureBlackHook { *; }
-keep class com.drkmuqeett.pureblack.config.PureBlackPrefs { *; }
-keep class com.drkmuqeett.pureblack.config.PureBlackPalette { *; }
-keep class com.drkmuqeett.pureblack.config.SableSettings { *; }
-keepnames class com.drkmuqeett.pureblack.core.** { *; }
-keepnames class com.drkmuqeett.pureblack.config.** { *; }

# Xposed API is compileOnly — silence the expected missing-class notes.
-dontwarn de.robv.android.xposed.**
-dontwarn de.robv.android.xposed.callbacks.**

# Kotlin runtime metadata is not needed inside the payload.
-dontwarn kotlin.**
-keepclassmembers class ** {
    @androidx.annotation.Keep *;
}

# Manifest components.
-keep class com.drkmuqeett.pureblack.SableApplication { *; }
-keep class com.drkmuqeett.pureblack.MainActivity { *; }

# View binding generated classes.
-keep class com.drkmuqeett.pureblack.databinding.** { *; }

# Keep data classes used by DiffUtil intact (equals/hashCode/componentN).
-keepclassmembers class com.drkmuqeett.pureblack.** {
    synthetic *** component*();
    synthetic *** copy*(...);
}
```

---
### `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  SablePureBlack — by @drkmuqeett
  Package: com.drkmuqeett.pureblack
  True pitch-black AMOLED dark mode for user-selected apps (LSPosed / Zygisk Vector).
-->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!--
      Package visibility (Android 11+). The module needs to:
        - enumerate launchable user apps for the per-app scope selector,
        - see the LSPosed / Vector manager packages for the deep-link button.
      QUERY_ALL_PACKAGES is intentionally NOT requested: the <queries> block below
      plus the implicit LAUNCHER-intent query in AppRepository covers both needs
      with the narrowest possible surface. No other permission is requested, and
      nothing here can touch display brightness or colour hardware.
    -->
    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
        <package android:name="org.lsposed.manager" />
        <package android:name="io.github.lsposed.manager" />
        <package android:name="org.lsposed.lspatch" />
        <package android:name="org.meowcat.edxposed.manager" />
        <package android:name="de.robv.android.xposed.installer" />
        <package android:name="com.solohsu.android.edxp.manager" />
        <package android:name="org.vector.manager" />
        <package android:name="io.github.vector.manager" />
    </queries>

    <application
        android:name=".SableApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:enableOnBackInvokedCallback="true"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.SablePureBlack"
        tools:targetApi="34">

        <!-- ==== LSPosed / Zygisk Vector module metadata ==== -->
        <meta-data
            android:name="xposedmodule"
            android:value="true" />
        <meta-data
            android:name="xposeddescription"
            android:value="@string/xposed_description" />
        <meta-data
            android:name="xposedminversion"
            android:value="82" />
        <!-- Makes the module's preference file readable in every scoped process,
             which is what enables live configuration updates over IPC. -->
        <meta-data
            android:name="xposedsharedprefs"
            android:value="true" />
        <meta-data
            android:name="xposedscope"
            android:resource="@array/xposed_scope" />

        <activity
            android:name=".MainActivity"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode|smallestScreenSize"
            android:exported="true"
            android:label="@string/app_name"
            android:launchMode="singleTop"
            android:theme="@style/Theme.SablePureBlack"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

---
### `app/src/main/assets/xposed_init`

```text
com.drkmuqeett.pureblack.core.PureBlackHook
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/MainActivity.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Companion screen: true #000000 Material 3 theme, #121212 elevated cards, one
 * flat RecyclerView with DiffUtil updates and spring-driven interactions.
 *
 * Everything the user changes is written straight into the cross-process
 * preference file the payload reads, so a hook picks the new palette up on the
 * next window attach — no module restart, no device reboot.
 */
package com.drkmuqeett.pureblack

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.drkmuqeett.pureblack.apps.ScopeStore
import com.drkmuqeett.pureblack.config.PaletteMath
import com.drkmuqeett.pureblack.config.PureBlackPrefs
import com.drkmuqeett.pureblack.config.SableSettings
import com.drkmuqeett.pureblack.config.SharedPrefSource
import com.drkmuqeett.pureblack.core.Probe
import com.drkmuqeett.pureblack.databinding.ActivityMainBinding
import com.drkmuqeett.pureblack.ui.HeaderState
import com.drkmuqeett.pureblack.ui.PaletteOption
import com.drkmuqeett.pureblack.ui.ProbeState
import com.drkmuqeett.pureblack.ui.Row
import com.drkmuqeett.pureblack.ui.SableAdapter
import com.drkmuqeett.pureblack.ui.SableCallbacks
import com.drkmuqeett.pureblack.ui.ScopeSheet
import com.drkmuqeett.pureblack.ui.ScopeState
import com.drkmuqeett.pureblack.ui.Springs

class MainActivity : AppCompatActivity(), SableCallbacks {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SableAdapter

    private var settings: SableSettings = SableSettings.DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureWindow()

        adapter = SableAdapter(this)
        binding.contentList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = DefaultItemAnimator().apply {
                // Cross-fades add nothing on a black background; plain add / move
                // animations keep the list cheap.
                supportsChangeAnimations = false
            }
            setHasFixedSize(false)
            adapter = this@MainActivity.adapter
        }

        supportFragmentManager.setFragmentResultListener(
            ScopeSheet.RESULT_KEY,
            this
        ) { _, _ ->
            // Re-render the scope summary row after the sheet closes.
            reloadSettings()
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        reloadSettings()
        render()
    }

    private fun configureWindow() {
        // Edge-to-edge, pure black bars: colours and appearance flags only —
        // never brightness, never a colour matrix.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun reloadSettings() {
        settings = SableSettings.from(SharedPrefSource(PureBlackPrefs.store(this)))
    }

    // ------------------------------------------------------------------- render

    private fun render() {
        val probe = Probe.inspect(this)
        val scope = ScopeStore.read(this)

        val paletteHex = PureBlackPrefs.paletteHex(settings.palette, settings.accentSeed)
        val header = HeaderState(
            probe = ProbeState(
                injected = probe.injected,
                managerLabel = probe.managerLabel,
                managerAvailable = true,
                ipcReady = probe.ipcReady
            ),
            paletteLabel = PureBlackPrefs.paletteLabel(settings.palette),
            paletteHex = paletteHex
        )

        val rows = buildList {
            add(Row.Header(header))
            add(Row.Section(getString(R.string.section_palette)))
            palettes().forEach { add(Row.Palette(it)) }
            if (settings.palette == PureBlackPrefs.PALETTE_CUSTOM_ACCENT) {
                add(Row.Accent(PaletteMath.ACCENT_PRESETS, settings.accentSeed))
            }
            add(Row.Section(getString(R.string.section_toggles)))
            add(
                Row.Toggle(
                    id = SableAdapter.TOGGLE_WEBVIEW,
                    title = getString(R.string.toggle_webview),
                    subtitle = getString(R.string.toggle_webview_sub),
                    checked = settings.webViewForceDark
                )
            )
            add(
                Row.Toggle(
                    id = SableAdapter.TOGGLE_TEXT,
                    title = getString(R.string.toggle_text),
                    subtitle = getString(R.string.toggle_text_sub),
                    checked = settings.textContrastBoost
                )
            )
            add(Row.Opacity(settings.overlayOpacityPercent))
            add(
                Row.ScopeHeader(
                    ScopeState(
                        apps = emptyList(),
                        query = "",
                        selection = scope.packages,
                        selectedMode = scope.isSelectedMode,
                        loading = false
                    )
                )
            )
            add(Row.Guide)
            add(Row.About)
        }
        adapter.submit(rows)
    }

    private fun palettes(): List<PaletteOption> = listOf(
        PaletteOption(
            id = PureBlackPrefs.PALETTE_PITCH_BLACK,
            title = getString(R.string.palette_pitch_black),
            subtitle = getString(R.string.palette_pitch_black_sub),
            swatch = PureBlackPrefs.COLOR_PITCH_BLACK,
            selected = settings.palette == PureBlackPrefs.PALETTE_PITCH_BLACK
        ),
        PaletteOption(
            id = PureBlackPrefs.PALETTE_DEEP_OBSIDIAN,
            title = getString(R.string.palette_obsidian),
            subtitle = getString(R.string.palette_obsidian_sub),
            swatch = PureBlackPrefs.COLOR_DEEP_OBSIDIAN,
            selected = settings.palette == PureBlackPrefs.PALETTE_DEEP_OBSIDIAN
        ),
        PaletteOption(
            id = PureBlackPrefs.PALETTE_CUSTOM_ACCENT,
            title = getString(R.string.palette_custom),
            subtitle = getString(R.string.palette_custom_sub),
            swatch = PaletteMath.customAccentColor(settings.accentSeed),
            selected = settings.palette == PureBlackPrefs.PALETTE_CUSTOM_ACCENT
        )
    )

    // ----------------------------------------------------------------- callbacks

    override fun onPaletteSelected(option: PaletteOption) {
        if (settings.palette == option.id) {
            // Re-tapping the active custom palette cycles its accent seed.
            if (option.id == PureBlackPrefs.PALETTE_CUSTOM_ACCENT) {
                cycleAccentSeed()
            }
            return
        }
        PureBlackPrefs.store(this).edit()
            .putInt(PureBlackPrefs.KEY_PALETTE, option.id)
            .putBoolean(PureBlackPrefs.KEY_MODULE_ENABLED, true)
            .apply()
        reloadSettings()
        render()
        pulseActivePalette(option)
        toast(getString(R.string.toast_saved))
    }

    override fun onAccentSelected(seed: Int) {
        PureBlackPrefs.store(this).edit()
            .putInt(PureBlackPrefs.KEY_CUSTOM_ACCENT_SEED, seed)
            .putInt(PureBlackPrefs.KEY_PALETTE, PureBlackPrefs.PALETTE_CUSTOM_ACCENT)
            .apply()
        reloadSettings()
        render()
    }

    override fun onToggleChanged(id: Int, checked: Boolean) {
        val editor = PureBlackPrefs.store(this).edit()
        when (id) {
            SableAdapter.TOGGLE_WEBVIEW ->
                editor.putBoolean(PureBlackPrefs.KEY_WEBVIEW_FORCE_DARK, checked)
            SableAdapter.TOGGLE_TEXT ->
                editor.putBoolean(PureBlackPrefs.KEY_TEXT_CONTRAST_BOOST, checked)
        }
        editor.putBoolean(PureBlackPrefs.KEY_MODULE_ENABLED, true).apply()
        reloadSettings()
        render()
    }

    override fun onOpacityChanged(percent: Int) {
        PureBlackPrefs.store(this).edit()
            .putInt(PureBlackPrefs.KEY_OVERLAY_OPACITY, percent.coerceIn(0, 100))
            .apply()
        settings = settings.copy(overlayOpacityPercent = percent.coerceIn(0, 100))
    }

    override fun onOpenScope() {
        if (supportFragmentManager.findFragmentByTag(ScopeSheet.TAG) != null) return
        ScopeSheet.newInstance().show(supportFragmentManager, ScopeSheet.TAG)
    }

    override fun onOpenManager() {
        val launched = Probe.launchFirst(this, Probe.managerIntents(this))
        if (!launched) {
            toast(getString(R.string.toast_manager_missing))
        }
    }

    override fun onOpenAppInfo() {
        val intent: Intent? = Probe.appInfoIntent(this)
        if (intent == null || !Probe.launchFirst(this, listOf(intent))) {
            toast(getString(R.string.toast_app_not_found))
        }
    }

    // ------------------------------------------------------------------- accents

    /** Cycles the custom accent seed through the built-in presets. */
    private fun cycleAccentSeed() {
        val presets = PaletteMath.ACCENT_PRESETS
        val currentIndex = presets.indexOf(settings.accentSeed).coerceAtLeast(0)
        val next = presets[(currentIndex + 1) % presets.size]
        PureBlackPrefs.store(this).edit()
            .putInt(PureBlackPrefs.KEY_CUSTOM_ACCENT_SEED, next)
            .putInt(PureBlackPrefs.KEY_PALETTE, PureBlackPrefs.PALETTE_CUSTOM_ACCENT)
            .apply()
        reloadSettings()
        render()
    }

    /** Soft spring pulse on the newly selected palette row. */
    private fun pulseActivePalette(option: PaletteOption) {
        // header + "Theme palette" section come first, then the three palettes.
        val index = 2 + palettes().indexOfFirst { it.id == option.id }
        if (index < 0) return
        binding.contentList.post {
            val holder = binding.contentList.findViewHolderForAdapterPosition(index) ?: return@post
            Springs.pulse(holder.itemView)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-assert pure black bars after rotation or a return from the
            // manager. Colour only — brightness is never touched.
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK
            binding.contentList.post { animateHeaderIn() }
        }
    }

    /** Header fades in with a spring once per focus gain. */
    private fun animateHeaderIn() {
        val holder = binding.contentList.findViewHolderForAdapterPosition(0) ?: return
        val view: View = holder.itemView
        if (view.alpha < 1f) {
            view.alpha = 0f
            Springs.fadeIn(view)
        }
    }

}
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/SableApplication.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 */
package com.drkmuqeett.pureblack

import android.app.Application
import com.drkmuqeett.pureblack.config.PureBlackPrefs

class SableApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        seedDefaults()
    }

    /**
     * Writes every preference with its default on first launch, so the payload
     * (which reads this same file from other processes) never has to guess and
     * a fresh install behaves exactly like a configured one.
     */
    private fun seedDefaults() {
        try {
            val store = PureBlackPrefs.store(this)
            if (store.contains(PureBlackPrefs.KEY_INSTALL_STAMP)) return
            store.edit()
                .putInt(PureBlackPrefs.KEY_PALETTE, PureBlackPrefs.DEFAULT_PALETTE)
                .putInt(
                    PureBlackPrefs.KEY_CUSTOM_ACCENT_SEED,
                    PureBlackPrefs.DEFAULT_CUSTOM_ACCENT_SEED
                )
                .putBoolean(
                    PureBlackPrefs.KEY_WEBVIEW_FORCE_DARK,
                    PureBlackPrefs.DEFAULT_WEBVIEW_FORCE_DARK
                )
                .putBoolean(
                    PureBlackPrefs.KEY_TEXT_CONTRAST_BOOST,
                    PureBlackPrefs.DEFAULT_TEXT_CONTRAST_BOOST
                )
                .putInt(
                    PureBlackPrefs.KEY_OVERLAY_OPACITY,
                    PureBlackPrefs.DEFAULT_OVERLAY_OPACITY
                )
                .putBoolean(
                    PureBlackPrefs.KEY_MODULE_ENABLED,
                    PureBlackPrefs.DEFAULT_MODULE_ENABLED
                )
                .putInt(PureBlackPrefs.KEY_TARGET_MODE, PureBlackPrefs.DEFAULT_TARGET_MODE)
                .putStringSet(PureBlackPrefs.KEY_SCOPED_APPS, emptySet())
                .putLong(PureBlackPrefs.KEY_INSTALL_STAMP, System.currentTimeMillis())
                .apply()
        } catch (_: Throwable) {
            // Never block app startup on preferences.
        }
    }
}
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/apps/AppRepository.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Installed-app discovery for the per-app scope selector.
 *
 * Only launchable packages are considered (the same set a user can actually
 * open), and only those this process may see under Android 11+ package
 * visibility. No permission is required: the manifest declares an implicit
 * LAUNCHER-intent <queries> entry.
 *
 * PackageManager work is synchronous here; the caller runs [load] on
 * Dispatchers.IO so the UI thread is never blocked.
 */
package com.drkmuqeett.pureblack.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import com.drkmuqeett.pureblack.config.PureBlackPrefs
import java.text.Collator
import java.util.Locale

/** One installed, launchable application. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val icon: Drawable?,
    /** Pre-normalised lowercase haystack: label + package name. */
    val searchKey: String
)

object AppRepository {

    /** Packages that must never appear as a scope target. */
    private val EXCLUDED: Set<String> = setOf(
        PureBlackPrefs.MODULE_PACKAGE,
        "com.drkmuqeett.pureblack.debug",
        "android",
        "com.android.systemui"
    )

    /**
     * Returns every launchable application this process may see, sorted
     * locale-aware by label.
     *
     * @param includeSystem keep pre-installed apps (default true) — users
     *        frequently want to force black on OEM gallery / clock / mail apps.
     * @param selfPackage the caller's own package, excluded from the result.
     */
    fun load(
        context: Context,
        includeSystem: Boolean = true,
        selfPackage: String? = null
    ): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved = try {
            pm.queryIntentActivities(intent, 0)
        } catch (_: Throwable) {
            emptyList()
        }

        val seen = HashSet<String>(resolved.size)
        val entries = ArrayList<AppEntry>(resolved.size)

        for (info in resolved) {
            val activityInfo = info.activityInfo ?: continue
            val packageName = activityInfo.packageName ?: continue
            if (packageName.isBlank() || packageName in EXCLUDED) continue
            if (packageName == selfPackage) continue
            if (!seen.add(packageName)) continue

            val appInfo: ApplicationInfo = activityInfo.applicationInfo ?: continue
            val isSystem: Boolean = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !includeSystem) continue

            val label = resolveLabel(pm, appInfo)
            entries += AppEntry(
                packageName = packageName,
                label = label,
                isSystem = isSystem,
                icon = resolveIcon(pm, appInfo),
                searchKey = (label + " " + packageName).lowercase(Locale.ROOT)
            )
        }

        return sortByLabel(entries)
    }

    /** Locale-aware sort by display label, package name as tie-breaker. */
    private fun sortByLabel(entries: List<AppEntry>): List<AppEntry> {
        val collator = try {
            Collator.getInstance(Locale.getDefault())
        } catch (_: Throwable) {
            null
        }
        return if (collator == null) {
            entries.sortedBy { it.searchKey }
        } else {
            entries.sortedWith { a, b ->
                val byLabel = collator.compare(a.label, b.label)
                if (byLabel != 0) byLabel else a.packageName.compareTo(b.packageName)
            }
        }
    }

    private fun resolveLabel(pm: PackageManager, appInfo: ApplicationInfo): String {
        val direct: String? = try {
            // loadLabel() is typed CharSequence (never null), but a malformed
            // APK can still end up with an empty string — normalise both.
            appInfo.loadLabel(pm).toString().trim()
        } catch (_: Throwable) {
            null
        }
        if (!direct.isNullOrEmpty() && direct != appInfo.packageName) return direct

        // Localised fallback: resolve the label with the app's own resources.
        return try {
            val label: String = pm.getResourcesForApplication(appInfo).getString(appInfo.labelRes)
            label.takeIf { it.isNotBlank() } ?: appInfo.packageName
        } catch (_: Throwable) {
            appInfo.packageName
        }
    }

    private fun resolveIcon(pm: PackageManager, appInfo: ApplicationInfo): Drawable? {
        try {
            appInfo.loadIcon(pm)?.let { return it }
        } catch (_: Throwable) {
            // Fall through to a resource-based lookup.
        }
        return try {
            pm.getResourcesForApplication(appInfo).getDrawable(appInfo.icon, null)
        } catch (_: Throwable) {
            null
        }
    }

    /** Filters [entries] by a free-text query, matching label and package. */
    fun filter(entries: List<AppEntry>, query: String): List<AppEntry> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return entries
        return entries.filter { entry -> entry.searchKey.contains(needle) }
    }

    /** Neutral icon for apps whose drawable could not be loaded. */
    fun fallbackIcon(): Drawable = ColorDrawable(0xFF121212.toInt())
}
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/apps/ScopeStore.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Persistence for the per-app scope selection.
 *
 * The selection lives in the same cross-process preference file the payload
 * reads, so toggling an app here changes hook behaviour on the next launch of
 * that app with no extra plumbing. A `StringSet` keeps writes atomic and cheap.
 */
package com.drkmuqeett.pureblack.apps

import android.content.Context
import android.content.SharedPreferences
import com.drkmuqeett.pureblack.config.PureBlackPrefs

object ScopeStore {

    /** Immutable snapshot of what the user has selected. */
    data class Selection(
        val packages: Set<String>,
        val targetMode: Int
    ) {
        val isSelectedMode: Boolean
            get() = targetMode == PureBlackPrefs.TARGET_MODE_SELECTED

        fun contains(packageName: String): Boolean = packages.contains(packageName)

        fun count(): Int = packages.size
    }

    fun read(context: Context): Selection {
        val store = PureBlackPrefs.store(context)
        return Selection(
            packages = readSet(store),
            targetMode = store.getInt(PureBlackPrefs.KEY_TARGET_MODE, PureBlackPrefs.DEFAULT_TARGET_MODE)
        )
    }

    /** Adds or removes [packageName] and persists the change immediately. */
    fun toggle(context: Context, packageName: String): Selection {
        val store = PureBlackPrefs.store(context)
        val current = readSet(store)
        val updated = LinkedHashSet(current)
        if (!updated.remove(packageName)) {
            updated.add(packageName)
        }
        store.edit().putStringSet(PureBlackPrefs.KEY_SCOPED_APPS, updated).apply()
        return Selection(
            packages = updated,
            targetMode = store.getInt(PureBlackPrefs.KEY_TARGET_MODE, PureBlackPrefs.DEFAULT_TARGET_MODE)
        )
    }

    /** Replaces the whole selection (used by "select all" / "clear"). */
    fun write(context: Context, packages: Set<String>): Selection {
        val store = PureBlackPrefs.store(context)
        store.edit().putStringSet(PureBlackPrefs.KEY_SCOPED_APPS, LinkedHashSet(packages)).apply()
        return Selection(
            packages = packages,
            targetMode = store.getInt(PureBlackPrefs.KEY_TARGET_MODE, PureBlackPrefs.DEFAULT_TARGET_MODE)
        )
    }

    /**
     * Switches between "every app in the LSPosed scope" and "only my list".
     * Selected mode starts empty, which the payload treats as "all", so flipping
     * the switch can never accidentally disable the module.
     */
    fun setTargetMode(context: Context, targetMode: Int) {
        PureBlackPrefs.store(context).edit()
            .putInt(PureBlackPrefs.KEY_TARGET_MODE, targetMode)
            .apply()
    }

    private fun readSet(store: SharedPreferences): Set<String> = try {
        store.getStringSet(PureBlackPrefs.KEY_SCOPED_APPS, null)
            ?.filterIsInstance<String>()
            ?.toSet()
            ?: emptySet()
    } catch (_: Throwable) {
        emptySet()
    }
}
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/config/PaletteMath.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Pure colour maths shared by the payload and the UI. No Android types on the
 * hot path, so the same functions that decide "is this surface light?" at
 * runtime also drive the palette previews in the companion app.
 */
package com.drkmuqeett.pureblack.config

/** Immutable ARGB helpers plus the custom-accent derivation. */
object PaletteMath {

    private const val RED_WEIGHT = 299
    private const val GREEN_WEIGHT = 587
    private const val BLUE_WEIGHT = 114
    private const val WEIGHT_SUM = RED_WEIGHT + GREEN_WEIGHT + BLUE_WEIGHT

    /** Rec. 601 luma of [color] in 0..255, alpha ignored. */
    fun luminance(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (RED_WEIGHT * r + GREEN_WEIGHT * g + BLUE_WEIGHT * b) / WEIGHT_SUM
    }

    fun alpha(color: Int): Int = (color ushr 24) and 0xFF

    fun red(color: Int): Int = (color shr 16) and 0xFF

    fun green(color: Int): Int = (color shr 8) and 0xFF

    fun blue(color: Int): Int = color and 0xFF

    /** Replaces the alpha channel of [color], keeping RGB. */
    fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)

    /** Linear interpolation between two colours, [fraction] in 0f..1f. */
    fun blend(from: Int, to: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val r = (red(from) + (red(to) - red(from)) * f).toInt()
        val g = (green(from) + (green(to) - green(from)) * f).toInt()
        val b = (blue(from) + (blue(to) - blue(from)) * f).toInt()
        return withAlpha((r shl 16) or (g shl 8) or b, alpha(from))
    }

    /**
     * Derives the "Custom Accent Dark" surface from a user accent seed.
     *
     * The seed only tints the surface: brightness is pinned into 10..22 luma so
     * the result stays a near-black AMOLED surface (pixels still effectively
     * off) while carrying the user's hue. A 12 % blend keeps red/green/blue
     * within 0x0A..0x16, which is #0A0A0A-ish with a tint.
     */
    fun customAccentColor(accentSeed: Int): Int {
        val tinted = blend(COLOR_DEEP_OBSIDIAN, accentSeed, TINT_FRACTION)
        // Clamp every channel into the AMOLED-safe band so a bright accent
        // (white, yellow) cannot lift the surface into visible grey.
        val r = red(tinted).coerceIn(MIN_CHANNEL, MAX_CHANNEL)
        val g = green(tinted).coerceIn(MIN_CHANNEL, MAX_CHANNEL)
        val b = blue(tinted).coerceIn(MIN_CHANNEL, MAX_CHANNEL)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** "#RRGGBB" for the given ARGB value. */
    fun hex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    private const val TINT_FRACTION = 0.12f
    private const val MIN_CHANNEL = 0x0A
    private const val MAX_CHANNEL = 0x16
    private const val COLOR_DEEP_OBSIDIAN = 0xFF0A0A0A.toInt()

    /** Accent choices offered by the palette switcher. */
    val ACCENT_PRESETS: List<Int> = listOf(
        0xFF3DD68C.toInt(), // Sable green (default)
        0xFF5AA9FF.toInt(), // Ice blue
        0xFFB388FF.toInt(), // Violet
        0xFFFF8A65.toInt(), // Ember
        0xFFFFD166.toInt(), // Amber
        0xFFEAEAEA.toInt()  // Neutral white
    )
}
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/config/PureBlackPrefs.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Shared preference contract between the companion UI and the Xposed payload.
 *
 * The manifest declares <meta-data android:name="xposedsharedprefs"
 * android:value="true"/>, so LSPosed / Zygisk Vector routes the module's
 * preference file into every scoped app process. Both sides therefore share a
 * single source of truth, which is what makes live config updates possible
 * without restarting the hooked app.
 */
package com.drkmuqeett.pureblack.config

import android.content.Context
import android.content.SharedPreferences

object PureBlackPrefs {

    /** Preference file name — identical in the UI process and in hooked processes. */
    const val FILE_NAME: String = "sablepureblack_config"

    /** Module package, used to resolve preferences across processes. */
    const val MODULE_PACKAGE: String = "com.drkmuqeett.pureblack"

    // ------------------------------------------------------------ palette ids
    /** True pitch black — pixels physically off on OLED. */
    const val PALETTE_PITCH_BLACK: Int = 0

    /** Deep Obsidian — soft dark contrast. */
    const val PALETTE_DEEP_OBSIDIAN: Int = 1

    /** Custom Accent Dark — user-tinted near-black surface. */
    const val PALETTE_CUSTOM_ACCENT: Int = 2

    // ---------------------------------------------------------------- keys
    const val KEY_PALETTE: String = "palette_id"
    const val KEY_CUSTOM_ACCENT_SEED: String = "custom_accent_seed"
    const val KEY_WEBVIEW_FORCE_DARK: String = "webview_force_dark"
    const val KEY_TEXT_CONTRAST_BOOST: String = "text_contrast_boost"
    const val KEY_OVERLAY_OPACITY: String = "overlay_opacity_percent"
    const val KEY_MODULE_ENABLED: String = "module_enabled"
    const val KEY_TARGET_MODE: String = "target_mode"
    const val KEY_SCOPED_APPS: String = "scoped_apps"
    const val KEY_INSTALL_STAMP: String = "install_stamp"

    // ------------------------------------------------------------- defaults
    const val DEFAULT_PALETTE: Int = PALETTE_PITCH_BLACK
    const val DEFAULT_CUSTOM_ACCENT_SEED: Int = 0xFF3DD68C.toInt()
    const val DEFAULT_WEBVIEW_FORCE_DARK: Boolean = true
    const val DEFAULT_TEXT_CONTRAST_BOOST: Boolean = true
    const val DEFAULT_OVERLAY_OPACITY: Int = 100
    const val DEFAULT_MODULE_ENABLED: Boolean = true

    /** Target mode: force every scoped process (default) or an allow-list only. */
    const val TARGET_MODE_ALL_SCOPED: Int = 0

    /** Target mode: only packages present in KEY_SCOPED_APPS. */
    const val TARGET_MODE_SELECTED: Int = 1

    const val DEFAULT_TARGET_MODE: Int = TARGET_MODE_ALL_SCOPED

    /** Brand colours used by the UI and by the palette previews. */
    const val COLOR_PITCH_BLACK: Int = 0xFF000000.toInt()
    const val COLOR_DEEP_OBSIDIAN: Int = 0xFF0A0A0A.toInt()

    /** Crisp off-white applied to text that would otherwise be unreadable. */
    const val COLOR_TEXT_ON_BLACK: Int = 0xFFEAEAEA.toInt()

    /** Opens the shared preference store for the current process. */
    fun store(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Human-readable palette label. */
    fun paletteLabel(palette: Int): String = when (palette) {
        PALETTE_DEEP_OBSIDIAN -> "Deep Obsidian"
        PALETTE_CUSTOM_ACCENT -> "Custom Accent Dark"
        else -> "Pitch Black"
    }

    /** Human-readable palette hex for subtitles and status tiles. */
    fun paletteHex(palette: Int, accentSeed: Int): String = when (palette) {
        PALETTE_DEEP_OBSIDIAN -> "#0A0A0A"
        PALETTE_CUSTOM_ACCENT -> PaletteMath.hex(PaletteMath.customAccentColor(accentSeed))
        else -> "#000000"
    }
}
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/config/SableSettings.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Immutable snapshot of the user's configuration, resolved from a [PrefSource]
 * (XSharedPreferences in the payload, SharedPreferences in the UI). Resolving
 * once keeps the hot view-hook paths free of preference I/O.
 */
package com.drkmuqeett.pureblack.config

import android.content.SharedPreferences

/** Minimal read surface implemented by every preference backend. */
interface PrefSource {
    fun getInt(key: String, defaultValue: Int): Int
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getString(key: String, defaultValue: String?): String?
    fun getStringSet(key: String, defaultValue: MutableSet<String>?): MutableSet<String>?
}

/** SharedPreferences-backed source (companion UI process). */
class SharedPrefSource(
    private val preferences: SharedPreferences
) : PrefSource {

    override fun getInt(key: String, defaultValue: Int): Int =
        try {
            preferences.getInt(key, defaultValue)
        } catch (_: Throwable) {
            defaultValue
        }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        try {
            preferences.getBoolean(key, defaultValue)
        } catch (_: Throwable) {
            defaultValue
        }

    override fun getString(key: String, defaultValue: String?): String? =
        try {
            preferences.getString(key, defaultValue)
        } catch (_: Throwable) {
            defaultValue
        }

    override fun getStringSet(key: String, defaultValue: MutableSet<String>?): MutableSet<String>? =
        try {
            @Suppress("UNCHECKED_CAST")
            (preferences.getStringSet(key, defaultValue) as? MutableSet<String>) ?: defaultValue
        } catch (_: Throwable) {
            defaultValue
        }
}

data class SableSettings(
    /** One of PureBlackPrefs.PALETTE_*. */
    val palette: Int,
    /** Resolved override colour for [palette]. */
    val overrideColor: Int,
    /** User accent seed used by PALETTE_CUSTOM_ACCENT and by status accents. */
    val accentSeed: Int,
    /** Force WebSettings.FORCE_DARK_ON on every WebView. */
    val webViewForceDark: Boolean,
    /** Lift unreadable dark text to #EAEAEA. */
    val textContrastBoost: Boolean,
    /** Alpha applied to forced surfaces, 100 = fully opaque. */
    val overlayOpacityPercent: Int,
    /** Master switch. */
    val enabled: Boolean,
    /** TARGET_MODE_ALL_SCOPED or TARGET_MODE_SELECTED. */
    val targetMode: Int,
    /** Allow-list used when [targetMode] is TARGET_MODE_SELECTED. */
    val scopedPackages: Set<String>
) {

    /** Alpha (0..255) the engine applies to forced surfaces. */
    val forcedAlpha: Int
        get() = (overlayOpacityPercent.coerceIn(0, 100) * 255) / 100

    /**
     * True when [packageName] should be processed.
     *
     * LSPosed already restricts hooking to the module's scope, so the default
     * mode trusts the manager. Selected mode adds a finer allow-list on top.
     */
    fun shouldProcess(packageName: String): Boolean {
        if (!enabled) return false
        if (targetMode == PureBlackPrefs.TARGET_MODE_ALL_SCOPED) return true
        if (scopedPackages.isEmpty()) return true
        return scopedPackages.contains(packageName)
    }

    companion object {

        /** Below this alpha an overlay is considered part of an icon/gradient. */
        const val ALPHA_KEEP_THRESHOLD: Int = 40

        /** Surfaces above this luma are considered "light" and forced dark. */
        const val LIGHT_LUMINANCE_THRESHOLD: Int = 45

        /** Text below this luma is lifted so it stays readable on black. */
        const val DARK_TEXT_LUMINANCE_THRESHOLD: Int = 90

        val DEFAULT: SableSettings = SableSettings(
            palette = PureBlackPrefs.DEFAULT_PALETTE,
            overrideColor = PureBlackPrefs.COLOR_PITCH_BLACK,
            accentSeed = PureBlackPrefs.DEFAULT_CUSTOM_ACCENT_SEED,
            webViewForceDark = PureBlackPrefs.DEFAULT_WEBVIEW_FORCE_DARK,
            textContrastBoost = PureBlackPrefs.DEFAULT_TEXT_CONTRAST_BOOST,
            overlayOpacityPercent = PureBlackPrefs.DEFAULT_OVERLAY_OPACITY,
            enabled = PureBlackPrefs.DEFAULT_MODULE_ENABLED,
            targetMode = PureBlackPrefs.DEFAULT_TARGET_MODE,
            scopedPackages = emptySet()
        )

        /** Resolves a settings snapshot from any backend, never throwing. */
        fun from(source: PrefSource?): SableSettings {
            if (source == null) return DEFAULT
            return try {
                val palette = source.getInt(PureBlackPrefs.KEY_PALETTE, PureBlackPrefs.DEFAULT_PALETTE)
                val accentSeed = source.getInt(
                    PureBlackPrefs.KEY_CUSTOM_ACCENT_SEED,
                    PureBlackPrefs.DEFAULT_CUSTOM_ACCENT_SEED
                )
                SableSettings(
                    palette = palette,
                    overrideColor = colorFor(palette, accentSeed),
                    accentSeed = accentSeed,
                    webViewForceDark = source.getBoolean(
                        PureBlackPrefs.KEY_WEBVIEW_FORCE_DARK,
                        PureBlackPrefs.DEFAULT_WEBVIEW_FORCE_DARK
                    ),
                    textContrastBoost = source.getBoolean(
                        PureBlackPrefs.KEY_TEXT_CONTRAST_BOOST,
                        PureBlackPrefs.DEFAULT_TEXT_CONTRAST_BOOST
                    ),
                    overlayOpacityPercent = source.getInt(
                        PureBlackPrefs.KEY_OVERLAY_OPACITY,
                        PureBlackPrefs.DEFAULT_OVERLAY_OPACITY
                    ),
                    enabled = source.getBoolean(
                        PureBlackPrefs.KEY_MODULE_ENABLED,
                        PureBlackPrefs.DEFAULT_MODULE_ENABLED
                    ),
                    targetMode = source.getInt(
                        PureBlackPrefs.KEY_TARGET_MODE,
                        PureBlackPrefs.DEFAULT_TARGET_MODE
                    ),
                    scopedPackages = source.getStringSet(PureBlackPrefs.KEY_SCOPED_APPS, null)
                        ?: emptySet()
                )
            } catch (_: Throwable) {
                DEFAULT
            }
        }

        /** Resolves the palette colour for [palette]. */
        fun colorFor(palette: Int, accentSeed: Int): Int = when (palette) {
            PureBlackPrefs.PALETTE_DEEP_OBSIDIAN -> PureBlackPrefs.COLOR_DEEP_OBSIDIAN
            PureBlackPrefs.PALETTE_CUSTOM_ACCENT -> PaletteMath.customAccentColor(accentSeed)
            else -> PureBlackPrefs.COLOR_PITCH_BLACK
        }
    }
}
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/core/Locales.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Locale extraction across API levels. Kept in the payload (not :config) so the
 * config layer compiles in both the payload and the UI without hidden APIs.
 *
 * Reflection is used for `LocaleList.get(index)` (hidden before API 34) and
 * always guarded, so a failure simply yields null.
 */
package com.drkmuqeett.pureblack.core

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

internal object Locales {

    /** Locale applied to [configuration], or null when unavailable. */
    fun primary(configuration: Configuration): Locale? =
        list(configuration.locales).firstOrNull() ?: defaultLocale()

    /** Locales applied to [resources], or null when unavailable. */
    fun primary(resources: Resources?): Locale? {
        if (resources == null) return null
        val locales = try {
            resources.configuration.locales
        } catch (_: Throwable) {
            null
        }
        return locales?.let { list(it) }?.firstOrNull() ?: defaultLocale()
    }

    /** Fully expanded LocaleList as a Kotlin list. */
    fun list(locales: LocaleList?): List<Locale> {
        if (locales == null) return emptyList()
        val size = try {
            locales.size()
        } catch (_: Throwable) {
            0
        }
        val result = ArrayList<Locale>(size)
        for (index in 0 until size) {
            indexLocale(locales, index)?.let(result::add)
        }
        return result
    }

    private fun indexLocale(locales: LocaleList, index: Int): Locale? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Public on API 34+, hidden before it — resolve reflectively.
            val method = LocaleList::class.java.getMethod("get", Int::class.javaPrimitiveType)
            method.invoke(locales, index) as? Locale
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }

    private fun defaultLocale(): Locale? = try {
        LocaleList.getDefault().let { list(it) }.firstOrNull() ?: Locale.getDefault()
    } catch (_: Throwable) {
        null
    }

    /** Two-letter language tag, or "??" when unknown. */
    fun languageTag(locale: Locale?): String =
        locale?.language?.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT) ?: "??"
}
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/core/Probe.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Live LSPosed / Zygisk Vector injection probe for the companion UI.
 *
 * A class in the companion app can only see `xposed` types when the framework
 * has injected them into *this* process — which happens only when the module is
 * enabled **and** scoped for its own package. The preference bridge is probed the
 * same way, so the header reflects real IPC health rather than a guess.
 *
 * No root, no `su`, no shell.
 */
package com.drkmuqeett.pureblack.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager

/** Result of probing the framework in the current process. */
data class InjectionProbe(
    val injected: Boolean,
    val ipcReady: Boolean,
    val managerPackage: String?,
    val managerLabel: String
) {
    val managerAvailable: Boolean
        get() = managerPackage != null
}

object Probe {

    private const val XPOSED_BRIDGE = "de.robv.android.xposed.XposedBridge"
    private const val XSHARED_PREFERENCES = "de.robv.android.xposed.XSharedPreferences"

    /** Manager packages, in preference order. */
    private val KNOWN_MANAGERS: List<Pair<String, String>> = listOf(
        "org.lsposed.manager" to "LSPosed Manager",
        "io.github.lsposed.manager" to "LSPosed Manager",
        "org.lsposed.lspatch" to "LSPatch",
        "org.vector.manager" to "Vector Manager",
        "io.github.vector.manager" to "Vector Manager",
        "org.meowcat.edxposed.manager" to "EdXposed Manager",
        "de.robv.android.xposed.installer" to "Xposed Installer",
        "com.solohsu.android.edxp.manager" to "EdXposed Manager"
    )

    /** Probes the current process and the installed managers. */
    fun inspect(context: Context?): InjectionProbe {
        val injected = isFrameworkInjected()
        val manager = findManager(context)
        return InjectionProbe(
            injected = injected,
            ipcReady = injected && canLoadXSharedPreferences(),
            managerPackage = manager?.first,
            managerLabel = manager?.second ?: "LSPosed Manager"
        )
    }

    /** True when framework classes are resolvable from this app's loader. */
    fun isFrameworkInjected(): Boolean = isLoadable(XPOSED_BRIDGE) || hasFrameworkStackFrame()

    /** True when the cross-process preference bridge is usable. */
    private fun canLoadXSharedPreferences(): Boolean = isLoadable(XSHARED_PREFERENCES)

    private fun isLoadable(className: String): Boolean = try {
        Class.forName(className, false, Probe::class.java.classLoader)
        true
    } catch (_: Throwable) {
        false
    }

    private fun hasFrameworkStackFrame(): Boolean = try {
        Throwable().stackTrace.any { frame ->
            val name = frame.className
            name.startsWith("de.robv.android.xposed") ||
                name.startsWith("org.lsposed") ||
                name.startsWith("io.github.lsposed") ||
                name.contains("XposedBridge")
        }
    } catch (_: Throwable) {
        false
    }

    /** First installed manager package + its display label. */
    private fun findManager(context: Context?): Pair<String, String>? {
        if (context == null) return null
        val pm = context.packageManager
        for ((packageName, label) in KNOWN_MANAGERS) {
            val installed = try {
                pm.getPackageInfo(packageName, 0)
                true
            } catch (_: Throwable) {
                false
            }
            if (installed) return packageName to label
        }
        return null
    }

    /**
     * Intents that open the manager's scope screen, best first, always ending
     * with a guaranteed system App-info fallback.
     */
    fun managerIntents(context: Context): List<Intent> {
        val intents = mutableListOf<Intent>()
        val manager = findManager(context)?.first

        if (manager != null) {
            try {
                context.packageManager.getLaunchIntentForPackage(manager)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intents += it
                }
            } catch (_: Throwable) {
                // Fall through to the deep links.
            }
            // LSPosed exposes a scope editor; the explicit component is a hint
            // and the URI scheme below is the documented fallback.
            intents += Intent(Intent.ACTION_VIEW).apply {
                setClassName(manager, "org.lsposed.manager.ui.activity.ModulesActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            intents += Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("lsposed://module?package=${context.packageName}")
                setPackage(manager)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        appInfoIntent(context)?.let { intents += it }
        return intents
    }

    /** System App-info screen for [packageName] (defaults to this module). */
    fun appInfoIntent(context: Context, packageName: String = context.packageName): Intent? = try {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } catch (_: Throwable) {
        null
    }

    /** Starts the first intent that resolves; false when none did. */
    fun launchFirst(context: Context, intents: List<Intent>): Boolean {
        val pm = context.packageManager
        for (intent in intents) {
            if (resolves(pm, intent)) {
                try {
                    context.startActivity(intent)
                    return true
                } catch (_: Throwable) {
                    // Try the next candidate.
                }
            }
        }
        return false
    }

    private fun resolves(pm: PackageManager, intent: Intent): Boolean = try {
        pm.resolveActivity(intent, 0) != null || pm.queryIntentActivities(intent, 0).isNotEmpty()
    } catch (_: Throwable) {
        false
    }
}
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/core/PureBlackHook.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Xposed / LSPosed entry point for SablePureBlack.
 *
 * Pipeline
 * ========
 *  1. Live config — XSharedPreferences bound through the `xposedsharedprefs`
 *     manifest bridge, with a `hasFileChanged()` poll on every window attach so
 *     palette / toggle changes apply without restarting the hooked app.
 *  2. Window & DecorView — Activity.onCreate / onResume / Dialog.show force a
 *     black window background, black status + navigation bars and
 *     decorView.isForceDarkAllowed = true.
 *  3. Luminance/alpha engine — setBackgroundColor, setBackground and
 *     setBackgroundResource are hooked; light surfaces (luma > 45) become the
 *     palette colour, transparent overlays (alpha < 40) survive untouched, and
 *     source alpha is re-applied so translucent white cards become translucent
 *     black cards instead of solid blocks. Resource verdicts are cached by id.
 *  4. WebView + text — FORCE_DARK_ON on every WebView; dark text (luma < 90) is
 *     lifted to #EAEAEA.
 *  5. Zero hardware interference — Settings.System.SCREEN_BRIGHTNESS,
 *     SCREEN_BRIGHTNESS_MODE, WindowManager.LayoutParams.screenBrightness,
 *     SurfaceFlinger colour matrices and display calibration are never read,
 *     never written and never hooked.
 */
package com.drkmuqeett.pureblack.core

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import com.drkmuqeett.pureblack.config.PaletteMath
import com.drkmuqeett.pureblack.config.PrefSource
import com.drkmuqeett.pureblack.config.PureBlackPrefs
import com.drkmuqeett.pureblack.config.SableSettings
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Payload logger — every diagnostic goes through the LSPosed log. */
internal object HookLog {

    private const val TAG = "SablePureBlack"

    fun d(message: String) {
        runCatching { XposedBridge.log("$TAG: $message") }
    }

    fun e(message: String, throwable: Throwable) {
        runCatching {
            XposedBridge.log("$TAG: $message :: ${android.util.Log.getStackTraceString(throwable)}")
        }
    }
}

@Suppress("DEPRECATION")
class PureBlackHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    // ------------------------------------------------------------------ state

    /** Current configuration snapshot for this process. */
    @Volatile
    private var settings: SableSettings = SableSettings.DEFAULT

    /** Live cross-process preference bridge, when the framework provides it. */
    @Volatile
    private var prefBridge: XPrefBridge? = null

    /** Resource-id → "is this drawable light?" verdict cache. */
    private val resourceVerdicts: MutableMap<Int, Boolean> = ConcurrentHashMap()

    /** Guards against recursion from our own setBackgroundColor calls. */
    private val selfCall: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    /** Throttles preference re-reads so the poll stays off the frame budget. */
    @Volatile
    private var lastRefreshAt: Long = 0L

    // -------------------------------------------------------------- lifecycle

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        // Nothing is hooked in the zygote: the module must never leak into
        // system_server or unrelated processes. Work happens per package.
        HookLog.d("initZygote(${startupParam.modulePath})")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val target = lpparam.packageName ?: return
        if (target == PureBlackPrefs.MODULE_PACKAGE || target == DEBUG_PACKAGE) {
            // The companion UI is already true black; never re-hook ourselves.
            return
        }

        try {
            attachPreferences(lpparam)
            if (!settings.enabled) {
                HookLog.d("disabled in prefs — skipping $target")
                return
            }
            if (!settings.shouldProcess(target)) {
                HookLog.d("not in selected scope — skipping $target")
                return
            }

            hookActivityLifecycle()
            hookBackgroundColor()
            hookBackgroundDrawable()
            hookBackgroundResource()
            hookTextColors()
            hookWebView()
            hookDialogs()
            hookApplicationCreate()

            HookLog.d(
                "hooks attached for $target " +
                    "(palette=${PureBlackPrefs.paletteLabel(settings.palette)}, " +
                    "opacity=${settings.overlayOpacityPercent}%, " +
                    "webview=${settings.webViewForceDark}, text=${settings.textContrastBoost})"
            )
        } catch (t: Throwable) {
            HookLog.e("failed to attach hooks for $target", t)
        }
    }

    /**
     * Binds the cross-process preference bridge.
     *
     * With `xposedsharedprefs=true` LSPosed resolves
     * XSharedPreferences(modulePackage, fileName) to the module's own file, so
     * the payload always observes exactly what the companion UI wrote.
     */
    private fun attachPreferences(lpparam: XC_LoadPackage.LoadPackageParam) {
        val bridge = XPrefBridge.create(lpparam.classLoader)
        prefBridge = bridge
        settings = SableSettings.from(bridge ?: fallbackPrefSource(lpparam))
        lastRefreshAt = System.currentTimeMillis()
    }

    /**
     * Last-resort backend: read the module's XML preference file directly.
     * Works on setups where the framework class is not injected but the file is
     * readable; otherwise degrades to defaults.
     */
    private fun fallbackPrefSource(lpparam: XC_LoadPackage.LoadPackageParam): PrefSource? = try {
        val context = android.app.AndroidAppHelper.currentApplication()
        if (context == null) {
            null
        } else {
            @Suppress("UNCHECKED_CAST")
            val clazz = Class.forName(
                "com.drkmuqeett.pureblack.config.SharedPrefSource",
                false,
                lpparam.classLoader
            )
            val constructor = clazz.getConstructor(android.content.SharedPreferences::class.java)
            constructor.newInstance(
                context.getSharedPreferences(PureBlackPrefs.FILE_NAME, android.content.Context.MODE_PRIVATE)
            ) as? PrefSource
        }
    } catch (_: Throwable) {
        null
    }

    /**
     * Re-reads configuration at most once every [REFRESH_INTERVAL_MS].
     * Called from every window attach, which is frequent enough to feel instant
     * and rare enough to be free.
     */
    private fun refreshSettingsIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshAt < REFRESH_INTERVAL_MS) return
        lastRefreshAt = now
        val bridge = prefBridge ?: return
        try {
            if (!bridge.hasFileChanged()) return
            bridge.reload()
            val updated = SableSettings.from(bridge)
            if (updated != settings) {
                settings = updated
                resourceVerdicts.clear()
                HookLog.d("live config updated: palette=${PureBlackPrefs.paletteLabel(updated.palette)}")
            }
        } catch (t: Throwable) {
            HookLog.e("refreshSettings", t)
        }
    }

    // ------------------------------------------------ 2. window / decor view

    private fun hookActivityLifecycle() {
        val onCreate = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                applyToActivity(activity, sweepTree = true)
            }
        }
        val onResume = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                applyToActivity(activity, sweepTree = false)
            }
        }
        safeHook("Activity.onCreate") {
            XposedHelpers.findAndHookMethod(Activity::class.java, "onCreate", Bundle::class.java, onCreate)
        }
        safeHook("Activity.onResume") {
            XposedHelpers.findAndHookMethod(Activity::class.java, "onResume", onResume)
        }
    }

    private fun applyToActivity(activity: Activity, sweepTree: Boolean) {
        if (!settings.enabled) return
        try {
            refreshSettingsIfStale()

            val window: Window = activity.window ?: return
            paintWindow(window)

            val decor: View = window.decorView
            forceDarkOn(decor)

            if (sweepTree) {
                // After inflation, then again for async content (lists, ads,
                // image loaders) that lands a few frames later.
                decor.post { sweep(decor) }
                decor.postDelayed({ sweep(decor) }, LATE_SWEEP_DELAY_MS)
            }
        } catch (t: Throwable) {
            HookLog.e("applyToActivity(${activity.javaClass.name})", t)
        }
    }

    /** Window-level black: background, status bar, navigation bar. */
    private fun paintWindow(window: Window) {
        val surface = settings.overrideColor
        try {
            window.setBackgroundDrawable(ColorDrawable(surface))
        } catch (_: Throwable) {
            // Some OEM windows reject a background swap; the decor sweep still
            // guarantees a dark result.
        }
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK
        } catch (t: Throwable) {
            HookLog.e("paintWindow bars", t)
        }
        try {
            // Light bar icons on black bars. Appearance flags only — no
            // brightness and no colour-matrix involvement whatsoever.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    0,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility and
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        } catch (_: Throwable) {
            // Cosmetic only — never fatal.
        }
    }

    /** Enables the framework's own force-dark on a subtree root. */
    private fun forceDarkOn(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            view.isForceDarkAllowed = true
        } catch (t: Throwable) {
            HookLog.e("forceDarkOn", t)
        }
    }

    // ------------------------------------- 3. luminance / alpha-aware engine

    /** Hooks View.setBackgroundColor(int). */
    private fun hookBackgroundColor() {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!settings.enabled || selfCall.get() == true) return
                val original = param.args.firstOrNull() as? Int ?: return
                val replacement = remapBackgroundColor(original) ?: return
                param.args[0] = replacement
            }
        }
        safeHook("View.setBackgroundColor") {
            XposedHelpers.findAndHookMethod(
                View::class.java,
                "setBackgroundColor",
                Int::class.javaPrimitiveType,
                hook
            )
        }
    }

    /** Hooks View.setBackground(Drawable) — flat coloured drawables only. */
    private fun hookBackgroundDrawable() {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!settings.enabled || selfCall.get() == true) return
                val drawable = param.args.firstOrNull() as? Drawable ?: return
                if (!isLightDrawable(drawable)) return
                param.args[0] = ColorDrawable(surfaceColorForDrawable(drawable))
            }
        }
        safeHook("View.setBackground") {
            XposedHelpers.findAndHookMethod(View::class.java, "setBackground", Drawable::class.java, hook)
        }
    }

    /** Hooks View.setBackgroundResource(int) with a cached resource verdict. */
    private fun hookBackgroundResource() {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!settings.enabled || selfCall.get() == true) return
                val view = param.thisObject as? View ?: return
                val resId = param.args.firstOrNull() as? Int ?: return
                if (resId == 0 || resId == View.NO_ID) return
                val context = view.context ?: return
                if (!isLightResource(context, resId)) return

                // Skip the framework call entirely and paint our surface.
                param.setResult(null)
                selfCall.set(true)
                try {
                    view.setBackgroundColor(settings.overrideColor)
                } finally {
                    selfCall.set(false)
                }
            }
        }
        safeHook("View.setBackgroundResource") {
            XposedHelpers.findAndHookMethod(
                View::class.java,
                "setBackgroundResource",
                Int::class.javaPrimitiveType,
                hook
            )
        }
    }

    /**
     * Decides whether a background colour must be replaced.
     *
     * @return the replacement colour, or null to leave the call untouched.
     */
    private fun remapBackgroundColor(color: Int): Int? {
        val alpha = PaletteMath.alpha(color)

        // Transparent / near-transparent overlays are part of icon, stroke and
        // image composition — never touch alpha < 40.
        if (alpha < SableSettings.ALPHA_KEEP_THRESHOLD) return null

        // Already dark: keep the app's authored contrast.
        val luma = PaletteMath.luminance(color)
        if (luma <= SableSettings.LIGHT_LUMINANCE_THRESHOLD) return null

        // Alpha-aware scaling: a 60 % white card becomes a 60 % black card,
        // scaled by the user's global opacity, never a solid opaque slab.
        val scaled = ((alpha.toLong() * settings.forcedAlpha) / 255L).toInt()
        val target = PaletteMath.withAlpha(settings.overrideColor, scaled.coerceIn(0, 255))
        return if (target == color) null else target
    }

    /** Surface colour for a light drawable, honouring the drawable's own alpha. */
    private fun surfaceColorForDrawable(drawable: Drawable): Int {
        val sourceAlpha = drawable.alpha.takeIf { it in 1..255 } ?: 255
        val scaled = ((sourceAlpha.toLong() * settings.forcedAlpha) / 255L).toInt()
        return PaletteMath.withAlpha(settings.overrideColor, scaled.coerceIn(0, 255))
    }

    /** True when the drawable paints a flat, light, non-transparent colour. */
    private fun isLightDrawable(drawable: Drawable): Boolean {
        val color = (drawable as? ColorDrawable)?.color ?: return false
        if (PaletteMath.alpha(color) < SableSettings.ALPHA_KEEP_THRESHOLD) return false
        return PaletteMath.luminance(color) > SableSettings.LIGHT_LUMINANCE_THRESHOLD
    }

    /**
     * Resolves and classifies a background resource. Resource ids are stable for
     * the lifetime of the process, so the verdict is cached: a recycling list of
     * 200 rows resolves one drawable, not two hundred.
     */
    private fun isLightResource(context: android.content.Context, resId: Int): Boolean {
        resourceVerdicts[resId]?.let { return it }
        val verdict = try {
            val drawable = context.getDrawable(resId)
            drawable != null && isLightDrawable(drawable)
        } catch (_: Throwable) {
            false
        }
        resourceVerdicts[resId] = verdict
        return verdict
    }

    /** Recursively normalises backgrounds and text colour under [root]. */
    private fun sweep(root: View) {
        if (!settings.enabled) return
        try {
            visit(root, 0)
        } catch (t: Throwable) {
            HookLog.e("sweep", t)
        }
    }

    private fun visit(view: View, depth: Int) {
        if (depth > MAX_TREE_DEPTH) return

        val background = view.background
        if (background != null && isLightDrawable(background)) {
            try {
                view.setBackgroundColor(settings.overrideColor)
            } catch (t: Throwable) {
                HookLog.e("sweep setBackgroundColor", t)
            }
        }

        liftText(view)
        if (view is WebView) applyWebViewDark(view)

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val child = view.getChildAt(index) ?: continue
                visit(child, depth + 1)
            }
        }
    }

    // ------------------------------------------ 4. WebView & text elevation

    /** Hooks TextView.setTextColor(int) so post-sweep text is lifted too. */
    private fun hookTextColors() {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!settings.enabled || !settings.textContrastBoost) return
                val color = param.args.firstOrNull() as? Int ?: return
                if (PaletteMath.alpha(color) < TEXT_ALPHA_FLOOR) return
                if (PaletteMath.luminance(color) >= SableSettings.DARK_TEXT_LUMINANCE_THRESHOLD) return
                param.args[0] = PureBlackPrefs.COLOR_TEXT_ON_BLACK
            }
        }
        safeHook("TextView.setTextColor") {
            XposedHelpers.findAndHookMethod(
                TextView::class.java,
                "setTextColor",
                Int::class.javaPrimitiveType,
                hook
            )
        }
    }

    /** Lifts genuinely unreadable text on a black surface to #EAEAEA. */
    private fun liftText(view: View) {
        if (!settings.textContrastBoost || view !is TextView) return
        try {
            val current = view.currentTextColor
            if (PaletteMath.alpha(current) < TEXT_ALPHA_FLOOR) return
            if (PaletteMath.luminance(current) >= SableSettings.DARK_TEXT_LUMINANCE_THRESHOLD) return

            if (view is EditText) {
                runCatching { view.setHintTextColor(PureBlackPrefs.COLOR_TEXT_ON_BLACK) }
            }
            view.setTextColor(PureBlackPrefs.COLOR_TEXT_ON_BLACK)
            view.setLinkTextColor(PureBlackPrefs.COLOR_TEXT_ON_BLACK)
        } catch (t: Throwable) {
            HookLog.e("liftText", t)
        }
    }

    /** Forces FORCE_DARK_ON on WebViews and their settings objects. */
    private fun hookWebView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            safeHook("WebSettings.setForceDark") {
                XposedHelpers.findAndHookMethod(
                    WebSettings::class.java,
                    "setForceDark",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!settings.enabled || !settings.webViewForceDark) return
                            // FORCE_DARK_ON == 2: a page-level opt-out is
                            // overridden so light HTML can never stay white.
                            param.args[0] = WebSettings.FORCE_DARK_ON
                        }
                    }
                )
            }
        }

        safeHook("WebView.onAttachedToWindow") {
            XposedHelpers.findAndHookMethod(
                WebView::class.java,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!settings.enabled) return
                        val webView = param.thisObject as? WebView ?: return
                        applyWebViewDark(webView)
                    }
                }
            )
        }
    }

    private fun applyWebViewDark(webView: WebView) {
        if (!settings.webViewForceDark) return
        try {
            webView.setBackgroundColor(settings.overrideColor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                webView.settings.forceDark = WebSettings.FORCE_DARK_ON
            }
        } catch (t: Throwable) {
            HookLog.e("applyWebViewDark", t)
        }
    }

    // ----------------------------------------------------- dialogs / application

    /** Dialogs and popups own their Window — paint those as well. */
    private fun hookDialogs() {
        safeHook("Dialog.show") {
            XposedHelpers.findAndHookMethod(
                Dialog::class.java,
                "show",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!settings.enabled) return
                        val dialog = param.thisObject as? Dialog ?: return
                        try {
                            val window = dialog.window ?: return
                            paintWindow(window)
                            val decor = window.decorView
                            forceDarkOn(decor)
                            sweep(decor)
                        } catch (t: Throwable) {
                            HookLog.e("Dialog.show", t)
                        }
                    }
                }
            )
        }
    }

    /** Re-reads configuration when the host app starts. */
    private fun hookApplicationCreate() {
        safeHook("Application.onCreate") {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        lastRefreshAt = 0L
                        refreshSettingsIfStale()
                    }
                }
            )
        }
    }

    // ------------------------------------------------------------ diagnostics

    private inline fun safeHook(label: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            HookLog.e("hook $label", t)
        }
    }

    // ------------------------------------------------ XSharedPreferences bridge

    /**
     * Thin reflective wrapper over `de.robv.android.xposed.XSharedPreferences`.
     *
     * Reflection is deliberate: `:core` must not require the framework class at
     * link time, so a build without the bridge (or a future API change) degrades
     * to the fallback source or to defaults instead of crashing the host app.
     */
    private class XPrefBridge private constructor(
        private val delegate: Any
    ) : PrefSource {

        override fun getInt(key: String, defaultValue: Int): Int =
            invoke("getInt", key, defaultValue) { (it as? Number)?.toInt() }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            invoke("getBoolean", key, defaultValue) { it as? Boolean }

        override fun getString(key: String, defaultValue: String?): String? {
            // The framework signature takes a non-null default, so a null caller
            // default is widened to "" and converted back to null on miss.
            val requested = defaultValue ?: ""
            val result = invoke("getString", key, requested) { it as? String }
            return if (result == requested && defaultValue == null) null else result
        }

        override fun getStringSet(
            key: String,
            defaultValue: MutableSet<String>?
        ): MutableSet<String>? = try {
            // java.util.Set is the framework's own signature (XSharedPreferences
            // implements SharedPreferences), so it must stay java-typed here.
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            val setType = java.util.Set::class.java
            val method = delegate.javaClass.getMethod("getStringSet", String::class.java, setType)
            @Suppress("UNCHECKED_CAST")
            val result = method.invoke(delegate, key, defaultValue ?: emptySet<String>())
            (result as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defaultValue
        } catch (_: Throwable) {
            defaultValue
        }

        /** True when the framework reports the file changed since last read. */
        fun hasFileChanged(): Boolean = try {
            delegate.javaClass.getMethod("hasFileChanged").invoke(delegate) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }

        /** Forces the bridge to re-read the preference file. */
        fun reload() {
            runCatching { delegate.javaClass.getMethod("reload").invoke(delegate) }
        }

        private fun <T : Any> invoke(
            methodName: String,
            key: String,
            fallback: T,
            convert: (Any?) -> T?
        ): T = try {
            val parameterType = when (fallback) {
                is Int -> Int::class.javaPrimitiveType
                is Boolean -> Boolean::class.javaPrimitiveType
                else -> String::class.java
            }
            val raw = delegate.javaClass
                .getMethod(methodName, String::class.java, parameterType)
                .invoke(delegate, key, fallback)
            convert(raw) ?: fallback
        } catch (_: Throwable) {
            fallback
        }

        companion object {

            private const val XSHARED_PREFERENCES = "de.robv.android.xposed.XSharedPreferences"

            /** Builds a bridge, or null when the framework class is unavailable. */
            fun create(classLoader: ClassLoader?): XPrefBridge? {
                val clazz = load(classLoader) ?: return null
                val attempts: List<() -> Any?> = listOf(
                    {
                        clazz.getConstructor(String::class.java, String::class.java)
                            .newInstance(PureBlackPrefs.MODULE_PACKAGE, PureBlackPrefs.FILE_NAME)
                    },
                    {
                        clazz.getConstructor(File::class.java)
                            .newInstance(modulePrefsFile(PureBlackPrefs.FILE_NAME))
                    },
                    {
                        clazz.getConstructor(String::class.java)
                            .newInstance(PureBlackPrefs.MODULE_PACKAGE)
                    }
                )
                for (build in attempts) {
                    try {
                        val instance = build() ?: continue
                        runCatching { clazz.getMethod("reload").invoke(instance) }
                        return XPrefBridge(instance)
                    } catch (_: Throwable) {
                        // Try the next constructor shape.
                    }
                }
                return null
            }

            private fun load(classLoader: ClassLoader?): Class<*>? {
                if (classLoader != null) {
                    try {
                        return Class.forName(XSHARED_PREFERENCES, false, classLoader)
                    } catch (_: Throwable) {
                        // Fall through to our own loader.
                    }
                }
                return try {
                    Class.forName(XSHARED_PREFERENCES, false, PureBlackHook::class.java.classLoader)
                } catch (_: Throwable) {
                    null
                }
            }

            /** /data/data/<module>/shared_prefs/<file>.xml */
            private fun modulePrefsFile(fileName: String): File = File(
                File(File(Environment.getDataDirectory(), "data"), PureBlackPrefs.MODULE_PACKAGE),
                "shared_prefs/$fileName.xml"
            )
        }
    }

    companion object {
        /** Debug variant package — matched so a debug build never hooks itself. */
        private const val DEBUG_PACKAGE = "com.drkmuqeett.pureblack.debug"
        private const val MAX_TREE_DEPTH = 96
        private const val LATE_SWEEP_DELAY_MS = 350L
        private const val REFRESH_INTERVAL_MS = 600L
        private const val TEXT_ALPHA_FLOOR = 0x40
    }
}
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/ui/AppScopeAdapter.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Adapter for the per-app scope selector bottom sheet.
 *
 * The full installed-app list is held here and filtered incrementally, so typing
 * in the search box never re-queries PackageManager. Rows are bound in a single
 * pass and the check state is applied without allocating.
 */
package com.drkmuqeett.pureblack.ui

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.drkmuqeett.pureblack.R
import com.drkmuqeett.pureblack.apps.AppEntry
import com.drkmuqeett.pureblack.apps.AppRepository
import com.drkmuqeett.pureblack.databinding.ItemAppBinding

class AppScopeAdapter(
    private val onToggle: (AppEntry, Boolean) -> Unit
) : RecyclerView.Adapter<AppScopeAdapter.AppHolder>() {

    private val items = mutableListOf<AppEntry>()
    private val selected = HashSet<String>()

    /** Replaces the visible list (already filtered by the caller). */
    fun submit(newItems: List<AppEntry>, selection: Set<String>) {
        val diff = DiffUtil.calculateDiff(Differ(items.toList(), newItems), true)
        items.clear()
        items.addAll(newItems)
        selected.clear()
        selected.addAll(selection)
        diff.dispatchUpdatesTo(this)
    }

    /** Updates check marks in place after a tap (no list diff needed). */
    fun updateSelection(selection: Set<String>, packageName: String) {
        selected.clear()
        selected.addAll(selection)
        val index = items.indexOfFirst { it.packageName == packageName }
        if (index >= 0) notifyItemChanged(index, PAYLOAD_CHECK)
    }

    fun isSelected(packageName: String): Boolean = selected.contains(packageName)

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppHolder =
        AppHolder(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: AppHolder, position: Int) {
        val entry = items[position]
        holder.bind(entry, selected.contains(entry.packageName))
    }

    override fun onBindViewHolder(
        holder: AppHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        val entry = items[position]
        holder.bindChecked(selected.contains(entry.packageName))
    }

    inner class AppHolder(
        private val binding: ItemAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var entry: AppEntry? = null

        init {
            binding.appRow.setOnClickListener {
                val current = entry ?: return@setOnClickListener
                val next = !isSelected(current.packageName)
                binding.appRow.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onToggle(current, next)
            }
        }

        fun bind(value: AppEntry, isSelected: Boolean) {
            entry = value
            val context = binding.root.context
            binding.appLabel.text = value.label
            binding.appPackage.text = value.packageName
            binding.appSystemBadge.visibility =
                if (value.isSystem) android.view.View.VISIBLE else android.view.View.GONE
            binding.appIcon.setImageDrawable(value.icon ?: AppRepository.fallbackIcon())
            binding.appLabel.setTextColor(
                ContextCompat.getColor(context, R.color.sable_text_primary)
            )
            bindChecked(isSelected)
        }

        fun bindChecked(isSelected: Boolean) {
            val context = binding.root.context
            binding.appCheck.isChecked = isSelected
            binding.appCheck.buttonTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.sable_accent else R.color.sable_stroke_bright
                )
            )
            binding.appRow.setBackgroundColor(
                if (isSelected) 0x0FFFFFFF else android.graphics.Color.TRANSPARENT
            )
        }
    }

    private class Differ(
        private val oldItems: List<AppEntry>,
        private val newItems: List<AppEntry>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldItems.size

        override fun getNewListSize(): Int = newItems.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldItems[oldItemPosition].packageName == newItems[newItemPosition].packageName

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val a = oldItems[oldItemPosition]
            val b = newItems[newItemPosition]
            return a.packageName == b.packageName &&
                a.label == b.label &&
                a.isSystem == b.isSystem
        }
    }

    private companion object {
        const val PAYLOAD_CHECK = "payload_check"
    }
}
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/ui/SableAdapter.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Main-screen adapter: one flat list of heterogeneous rows driven by DiffUtil.
 *
 * Payload updates keep toggles and palette selections cheap (a single row's
 * visual state changes, not the whole item), and the spring helpers are only
 * used for render-thread properties.
 */
package com.drkmuqeett.pureblack.ui

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.drkmuqeett.pureblack.R
import com.drkmuqeett.pureblack.config.PaletteMath
import com.drkmuqeett.pureblack.databinding.ItemAboutBinding
import com.drkmuqeett.pureblack.databinding.ItemAccentBinding
import com.drkmuqeett.pureblack.databinding.ItemGuideBinding
import com.drkmuqeett.pureblack.databinding.ItemHeaderBinding
import com.drkmuqeett.pureblack.databinding.ItemOpacityBinding
import com.drkmuqeett.pureblack.databinding.ItemPaletteBinding
import com.drkmuqeett.pureblack.databinding.ItemScopeEntryBinding
import com.drkmuqeett.pureblack.databinding.ItemSectionTitleBinding
import com.drkmuqeett.pureblack.databinding.ItemToggleBinding

/** Callbacks the screen implements. */
interface SableCallbacks {
    fun onPaletteSelected(option: PaletteOption)
    fun onAccentSelected(seed: Int)
    fun onToggleChanged(id: Int, checked: Boolean)
    fun onOpacityChanged(percent: Int)
    fun onOpenScope()
    fun onOpenManager()
    fun onOpenAppInfo()
}

class SableAdapter(
    private val callbacks: SableCallbacks
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = mutableListOf<Row>()

    fun submit(newRows: List<Row>) {
        val diff = DiffUtil.calculateDiff(Differ(rows.toList(), newRows), true)
        rows.clear()
        rows.addAll(newRows)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Header -> TYPE_HEADER
        is Row.Section -> TYPE_SECTION
        is Row.Palette -> TYPE_PALETTE
        is Row.Toggle -> TYPE_TOGGLE
        is Row.Accent -> TYPE_ACCENT
        is Row.Opacity -> TYPE_OPACITY
        is Row.ScopeHeader -> TYPE_SCOPE_ENTRY
        else -> TYPE_GUIDE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemHeaderBinding.inflate(inflater, parent, false))
            TYPE_SECTION -> SectionHolder(ItemSectionTitleBinding.inflate(inflater, parent, false))
            TYPE_PALETTE -> PaletteHolder(ItemPaletteBinding.inflate(inflater, parent, false))
            TYPE_TOGGLE -> ToggleHolder(ItemToggleBinding.inflate(inflater, parent, false))
            TYPE_ACCENT -> AccentHolder(ItemAccentBinding.inflate(inflater, parent, false))
            TYPE_OPACITY -> OpacityHolder(ItemOpacityBinding.inflate(inflater, parent, false))
            TYPE_SCOPE_ENTRY -> ScopeEntryHolder(ItemScopeEntryBinding.inflate(inflater, parent, false))
            TYPE_GUIDE -> GuideHolder(ItemGuideBinding.inflate(inflater, parent, false))
            else -> AboutHolder(ItemAboutBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderHolder).bind(row.state)
            is Row.Section -> (holder as SectionHolder).bind(row.title)
            is Row.Palette -> (holder as PaletteHolder).bind(row.option)
            is Row.Toggle -> (holder as ToggleHolder).bind(row)
            is Row.Accent -> (holder as AccentHolder).bind(row)
            is Row.Opacity -> (holder as OpacityHolder).bind(row.percent)
            is Row.ScopeHeader -> (holder as ScopeEntryHolder).bind(row.state)
            is Row.Guide -> (holder as GuideHolder).bind(callbacks)
            is Row.About -> (holder as AboutHolder).bind(callbacks)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        when (val row = rows[position]) {
            is Row.Palette -> (holder as? PaletteHolder)?.bindSelection(row.option)
            is Row.Toggle -> (holder as? ToggleHolder)?.bindChecked(row.checked)
            is Row.Accent -> (holder as? AccentHolder)?.bind(row)
            is Row.ScopeHeader -> (holder as? ScopeEntryHolder)?.bind(row.state)
            else -> super.onBindViewHolder(holder, position, payloads)
        }
    }

    // ------------------------------------------------------------------ header

    private class HeaderHolder(
        private val binding: ItemHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(state: HeaderState) {
            val context = binding.root.context
            val accent = ContextCompat.getColor(
                context,
                if (state.probe.injected) R.color.sable_active else R.color.sable_inactive
            )
            binding.statusText.text = state.probe.statusLabel
            binding.statusText.setTextColor(accent)
            binding.statusDot.backgroundTintList = ColorStateList.valueOf(accent)
            binding.statusPill.backgroundTintList = ColorStateList.valueOf(withAlpha(accent, 0x26))

            binding.statusDetail.text = if (state.probe.injected) {
                context.getString(
                    R.string.probe_active_detail,
                    state.probe.managerLabel,
                    state.paletteLabel
                )
            } else {
                context.getString(R.string.probe_inactive_detail, state.probe.managerLabel)
            }

            binding.ipcBadge.text = context.getString(
                if (state.probe.ipcReady) R.string.probe_ipc_ok else R.string.probe_ipc_missing
            )
            binding.ipcBadge.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (state.probe.ipcReady) R.color.sable_text_secondary else R.color.sable_warning
                )
            )
        }

        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)
    }

    private class SectionHolder(
        private val binding: ItemSectionTitleBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.sectionTitle.text = title
        }
    }

    // ----------------------------------------------------------------- palette

    private inner class PaletteHolder(
        private val binding: ItemPaletteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var option: PaletteOption? = null

        init {
            binding.paletteRow.setOnClickListener {
                option?.let {
                    binding.paletteRow.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    Springs.press(binding.paletteRow, true)
                    binding.paletteRow.postDelayed(
                        { Springs.press(binding.paletteRow, false) },
                        PRESS_SETTLE_MS
                    )
                    callbacks.onPaletteSelected(it)
                }
            }
        }

        fun bind(value: PaletteOption) {
            option = value
            binding.paletteTitle.text = value.title
            binding.paletteSubtitle.text = value.subtitle
            bindSelection(value)
        }

        fun bindSelection(value: PaletteOption) {
            option = value
            val context = binding.root.context
            binding.paletteSwatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(value.swatch)
                setStroke(dp(context, 1), ContextCompat.getColor(context, R.color.sable_stroke_bright))
            }
            binding.paletteCheck.visibility = if (value.selected) View.VISIBLE else View.INVISIBLE
            binding.paletteRow.setBackgroundResource(
                if (value.selected) R.drawable.bg_card_selected else R.drawable.bg_card_ripple
            )
            binding.paletteSubtitle.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (value.selected) R.color.sable_text_secondary else R.color.sable_text_tertiary
                )
            )
        }
    }

    // ------------------------------------------------------------------ toggle

    private inner class ToggleHolder(
        private val binding: ItemToggleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var row: Row.Toggle? = null

        init {
            binding.toggleRow.setOnClickListener { row?.let { emit(it) } }
        }

        fun bind(value: Row.Toggle) {
            row = value
            binding.toggleTitle.text = value.title
            binding.toggleSubtitle.text = value.subtitle
            bindChecked(value.checked)
        }

        fun bindChecked(checked: Boolean) {
            val context = binding.root.context
            binding.toggleSwitch.isChecked = checked
            binding.toggleSwitch.thumbTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (checked) R.color.sable_black else R.color.sable_accent_dim
                )
            )
            binding.toggleSwitch.trackTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (checked) R.color.sable_accent else R.color.sable_stroke_bright
                )
            )
        }

        private fun emit(value: Row.Toggle) {
            binding.toggleRow.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Springs.press(binding.toggleRow, true)
            binding.toggleRow.postDelayed(
                { Springs.press(binding.toggleRow, false) },
                PRESS_SETTLE_MS
            )
            callbacks.onToggleChanged(value.id, !value.checked)
        }
    }

    // ------------------------------------------------------------------ accent

    private inner class AccentHolder(
        private val binding: ItemAccentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(value: Row.Accent) {
            val context = binding.root.context
            binding.accentRow.removeAllViews()
            val size = dp(context, 38)
            val gap = dp(context, 10)
            value.seeds.forEach { seed ->
                val dot = android.view.View(context)
                val params = android.widget.LinearLayout.LayoutParams(size, size)
                params.marginEnd = gap
                dot.layoutParams = params
                dot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(seed)
                    setStroke(
                        dp(context, if (seed == value.selectedSeed) 3 else 1),
                        ContextCompat.getColor(
                            context,
                            if (seed == value.selectedSeed) R.color.sable_accent else R.color.sable_stroke_bright
                        )
                    )
                }
                dot.contentDescription = PaletteMath.hex(seed)
                dot.setOnClickListener {
                    dot.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    callbacks.onAccentSelected(seed)
                }
                binding.accentRow.addView(dot)
            }
        }
    }

    // ----------------------------------------------------------------- opacity

    private inner class OpacityHolder(
        private val binding: ItemOpacityBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var suppress = false

        init {
            binding.opacitySeek.setOnSeekBarChangeListener(
                object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: android.widget.SeekBar,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        binding.opacityValue.text =
                            binding.root.context.getString(R.string.opacity_value, progress)
                        if (fromUser && !suppress) {
                            callbacks.onOpacityChanged(progress)
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar) = Unit

                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar) = Unit
                }
            )
        }

        fun bind(percent: Int) {
            suppress = true
            binding.opacitySeek.progress = percent.coerceIn(0, 100)
            binding.opacityValue.text = binding.root.context.getString(R.string.opacity_value, percent)
            suppress = false
        }
    }

    // -------------------------------------------------------------- scope entry

    private inner class ScopeEntryHolder(
        private val binding: ItemScopeEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.scopeEntryRow.setOnClickListener { callbacks.onOpenScope() }
        }

        fun bind(state: ScopeState) {
            val context = binding.root.context
            val mode = if (state.selectedMode) "only selected apps" else "all scoped apps"
            binding.scopeEntrySummary.text = context.getString(
                R.string.scope_entry_summary,
                state.selectedCount,
                state.totalCount,
                mode
            )
        }
    }

    // ------------------------------------------------------------------- guide

    private inner class GuideHolder(
        private val binding: ItemGuideBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.openManagerButton.setOnClickListener { callbacks.onOpenManager() }
        }

        fun bind(callbacks: SableCallbacks) {
            binding.openManagerButton.setOnClickListener { callbacks.onOpenManager() }
        }
    }

    private inner class AboutHolder(
        private val binding: ItemAboutBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(callbacks: SableCallbacks) {
            binding.openAppInfo.setOnClickListener { callbacks.onOpenAppInfo() }
        }
    }

    // -------------------------------------------------------------------- diff

    private class Differ(
        private val oldRows: List<Row>,
        private val newRows: List<Row>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldRows.size

        override fun getNewListSize(): Int = newRows.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val a = oldRows[oldItemPosition]
            val b = newRows[newItemPosition]
            return when {
                a is Row.Header && b is Row.Header -> true
                a is Row.Section && b is Row.Section -> a.title == b.title
                a is Row.Palette && b is Row.Palette -> a.option.id == b.option.id
                a is Row.Toggle && b is Row.Toggle -> a.id == b.id
                a is Row.Opacity && b is Row.Opacity -> true
                a is Row.ScopeHeader && b is Row.ScopeHeader -> true
                a is Row.Guide && b is Row.Guide -> true
                a is Row.About && b is Row.About -> true
                else -> false
            }
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldRows[oldItemPosition] == newRows[newItemPosition]

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            val a = oldRows[oldItemPosition]
            val b = newRows[newItemPosition]
            return when {
                a is Row.Palette && b is Row.Palette && a.option.selected != b.option.selected ->
                    PAYLOAD_SELECTION
                a is Row.Toggle && b is Row.Toggle && a.checked != b.checked -> PAYLOAD_CHECKED
                else -> null
            }
        }
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_SECTION = 1
        const val TYPE_PALETTE = 2
        const val TYPE_TOGGLE = 3
        const val TYPE_ACCENT = 4
        const val TYPE_OPACITY = 5
        const val TYPE_SCOPE_ENTRY = 6
        const val TYPE_GUIDE = 7
        const val TYPE_ABOUT = 8

        /** How long a pressed row stays slightly scaled down. */
        const val PRESS_SETTLE_MS = 90L

        const val PAYLOAD_SELECTION = "payload_selection"
        const val PAYLOAD_CHECKED = "payload_checked"

        /** Toggle identifiers used by [Row.Toggle]. */
        const val TOGGLE_WEBVIEW = 0
        const val TOGGLE_TEXT = 1

        fun dp(context: android.content.Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()
    }
}
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/ui/ScopeSheet.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Searchable per-app scope selector in a Material 3 bottom sheet.
 *
 * Installed apps are loaded once, off the main thread; the search box filters the
 * in-memory list on every keystroke (O(n) over a pre-built lowercase haystack, no
 * PackageManager traffic). Toggling an app writes straight to the shared
 * preference file the payload reads.
 */
package com.drkmuqeett.pureblack.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
// Extension import required: viewLifecycleOwner.lifecycleScope comes from here.
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.drkmuqeett.pureblack.R
import com.drkmuqeett.pureblack.apps.AppEntry
import com.drkmuqeett.pureblack.apps.AppRepository
import com.drkmuqeett.pureblack.apps.ScopeStore
import com.drkmuqeett.pureblack.config.PureBlackPrefs
import com.drkmuqeett.pureblack.databinding.SheetScopeBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScopeSheet : BottomSheetDialogFragment() {

    private var _binding: SheetScopeBinding? = null
    private val binding: SheetScopeBinding
        get() = checkNotNull(_binding) { "ScopeSheet accessed after onDestroyView" }

    private lateinit var adapter: AppScopeAdapter

    private var allApps: List<AppEntry> = emptyList()
    private var selection: Set<String> = emptySet()
    private var selectedMode: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetScopeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val stored = ScopeStore.read(context)
        selection = stored.packages
        selectedMode = stored.isSelectedMode

        adapter = AppScopeAdapter(onToggle = ::toggleApp)
        binding.sheetList.layoutManager = LinearLayoutManager(context)
        binding.sheetList.adapter = adapter

        binding.selectedModeSwitch.isChecked = selectedMode
        binding.selectedModeSwitch.thumbTintList =
            android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.sable_black)
            )
        binding.selectedModeSwitch.trackTintList =
            android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.sable_accent)
            )
        binding.selectedModeRow.setOnClickListener {
            selectedMode = !selectedMode
            binding.selectedModeSwitch.isChecked = selectedMode
            ScopeStore.setTargetMode(
                context,
                if (selectedMode) {
                    PureBlackPrefs.TARGET_MODE_SELECTED
                } else {
                    PureBlackPrefs.TARGET_MODE_ALL_SCOPED
                }
            )
            notifyChanged()
            refreshVisibleList()
        }

        binding.searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                refreshVisibleList()
            }
        })

        expandSheet()
        loadApps()
    }

    /** Loads installed apps off the main thread, then renders. */
    private fun loadApps() {
        val appContext = requireContext().applicationContext
        val selfPackage = requireContext().packageName
        binding.sheetEmpty.visibility = View.VISIBLE
        binding.sheetEmptyText.setText(R.string.scope_loading)

        viewLifecycleOwner.lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                AppRepository.load(
                    context = appContext,
                    includeSystem = true,
                    selfPackage = selfPackage
                )
            }
            if (_binding == null) return@launch
            allApps = apps
            refreshVisibleList()
        }
    }

    private fun refreshVisibleList() {
        val query = binding.searchField.text?.toString().orEmpty()
        val filtered = AppRepository.filter(allApps, query)
        adapter.submit(filtered, selection)

        binding.sheetCount.text = getString(R.string.scope_count, selection.size, allApps.size)

        val empty = filtered.isEmpty()
        binding.sheetEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.sheetList.visibility = if (empty) View.GONE else View.VISIBLE
        if (empty) {
            binding.sheetEmptyText.text = if (allApps.isEmpty()) {
                getString(R.string.scope_loading)
            } else {
                getString(R.string.scope_empty, query)
            }
        }
    }

    private fun toggleApp(entry: AppEntry, requestedState: Boolean) {
        val updated = ScopeStore.toggle(requireContext(), entry.packageName)
        selection = updated.packages
        adapter.updateSelection(selection, entry.packageName)
        binding.sheetCount.text = getString(R.string.scope_count, selection.size, allApps.size)
        // The row is re-rendered from the persisted state, not from the tap, so
        // a failed write can never leave the checkbox lying about the config.
        if (requestedState != selection.contains(entry.packageName)) {
            refreshVisibleList()
        }
        notifyChanged()
    }

    private fun notifyChanged() {
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(
                RESULT_COUNT to selection.size,
                RESULT_MODE to selectedMode
            )
        )
    }

    private fun expandSheet() {
        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.let { sheet ->
            sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            sheet.behavior.skipCollapsed = true
            sheet.behavior.isDraggable = true
        }
    }

    override fun onDestroyView() {
        binding.sheetList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "ScopeSheet"
        const val RESULT_KEY = "scope_sheet_result"
        const val RESULT_COUNT = "selected_count"
        const val RESULT_MODE = "selected_mode"

        fun newInstance(): ScopeSheet = ScopeSheet()
    }
}
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/ui/Springs.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Spring physics helpers built on androidx.dynamicanimation.
 *
 * Only render-thread properties (alpha, scale, translation) are animated, so
 * every effect here is a zero-layout-pass operation and holds 60fps+ on modest
 * hardware. Each helper cancels the previous animation it started on that view.
 */
package com.drkmuqeett.pureblack.ui

import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

object Springs {

    /** Snappy, no overshoot — for appearance and press feedback. */
    private const val STIFFNESS_SNAPPY = 1_500f
    private const val DAMPING_SNAPPY = 0.85f

    /** Gentle float used by the header status pill. */
    private const val STIFFNESS_SOFT = 900f
    private const val DAMPING_SOFT = 0.8f

    private val ALPHA = DynamicAnimation.ALPHA
    private val SCALE_X = DynamicAnimation.SCALE_X
    private val SCALE_Y = DynamicAnimation.SCALE_Y

    /** Maps a view to the running animations started by this helper. */
    private val running = java.util.WeakHashMap<View, MutableList<SpringAnimation>>()

    /** Fades [view] in with a spring, keeping its translation intact. */
    fun fadeIn(view: View, stiffness: Float = STIFFNESS_SNAPPY) {
        animate(view, ALPHA, 1f, stiffness, DAMPING_SNAPPY)
    }

    /** Scales [view] to [value] on both axes. */
    fun scaleTo(view: View, value: Float, stiffness: Float = STIFFNESS_SNAPPY) {
        animate(view, SCALE_X, value, stiffness, DAMPING_SNAPPY)
        animate(view, SCALE_Y, value, stiffness, DAMPING_SNAPPY)
    }

    /** Springs [view] back to its resting presentation. */
    fun settle(view: View) {
        animate(view, ALPHA, 1f, STIFFNESS_SNAPPY, DAMPING_SNAPPY)
        animate(view, SCALE_X, 1f, STIFFNESS_SNAPPY, DAMPING_SNAPPY)
        animate(view, SCALE_Y, 1f, STIFFNESS_SNAPPY, DAMPING_SNAPPY)
    }

    /** A soft attention pulse: settle → slight lift → settle. */
    fun pulse(view: View) {
        animate(view, SCALE_X, 1.06f, STIFFNESS_SOFT, DAMPING_SOFT, onEnd = {
            animate(view, SCALE_X, 1f, STIFFNESS_SOFT, DAMPING_SOFT)
            animate(view, SCALE_Y, 1f, STIFFNESS_SOFT, DAMPING_SOFT)
        })
        animate(view, SCALE_Y, 1.06f, STIFFNESS_SOFT, DAMPING_SOFT)
    }

    /** Press-in / press-out feedback for a tappable row. */
    fun press(view: View, pressed: Boolean) {
        scaleTo(view, if (pressed) 0.985f else 1f, STIFFNESS_SNAPPY)
    }

    /** Cancels every animation this helper started on [view]. */
    fun cancel(view: View) {
        running.remove(view)?.forEach { animation ->
            runCatching { animation.cancel() }
        }
    }

    private fun animate(
        view: View,
        property: DynamicAnimation.ViewProperty,
        target: Float,
        stiffness: Float,
        damping: Float,
        onEnd: (() -> Unit)? = null
    ) {
        val animation = SpringAnimation(view, property).apply {
            spring = SpringForce(target).apply {
                this.stiffness = stiffness
                this.dampingRatio = damping
            }
        }
        if (onEnd != null) {
            animation.addEndListener { _, canceled, _ ->
                if (!canceled) onEnd()
            }
        }
        // Replace any in-flight animation for the same property on this view.
        running[view]?.filter { it != animation }?.let { list ->
            list.forEach { runCatching { it.cancel() } }
        }
        running.getOrPut(view) { mutableListOf() }.add(animation)
        runCatching { animation.start() }
    }
}
```

---
### `app/src/main/java/com/drkmuqeett/pureblack/ui/UiState.kt`

```kotlin
/*
 * SablePureBlack — by @drkmuqeett
 * Package: com.drkmuqeett.pureblack
 *
 * Immutable UI state for the companion screen. Every value the screen renders
 * lives here, so a rebind is a pure function of state and DiffUtil can compare
 * rows cheaply.
 */
package com.drkmuqeett.pureblack.ui

import androidx.annotation.ColorInt
import com.drkmuqeett.pureblack.apps.AppEntry

/** Live LSPosed injection probe result. */
data class ProbeState(
    /** Framework classes are injected in this process. */
    val injected: Boolean,
    /** Manager package label resolved on this device. */
    val managerLabel: String,
    /** True when a manager app is installed and could be launched. */
    val managerAvailable: Boolean,
    /** Preference bridge readable from this process (IPC healthy). */
    val ipcReady: Boolean
) {
    val statusLabel: String
        get() = if (injected) "Active" else "Not Activated in LSPosed"
}

/** Header banner content. */
data class HeaderState(
    val probe: ProbeState,
    val paletteLabel: String,
    val paletteHex: String
)

/** One palette row in the theme switcher. */
data class PaletteOption(
    val id: Int,
    val title: String,
    val subtitle: String,
    @ColorInt val swatch: Int,
    val selected: Boolean
)

/** Scope selector content. */
data class ScopeState(
    val apps: List<AppEntry>,
    val query: String,
    val selection: Set<String>,
    val selectedMode: Boolean,
    val loading: Boolean
) {
    val totalCount: Int
        get() = apps.size

    val selectedCount: Int
        get() = selection.size
}

/** Sealed row model consumed by [SableAdapter]. */
sealed interface Row {
    data class Header(val state: HeaderState) : Row
    data class Section(val title: String) : Row
    data class Palette(val option: PaletteOption) : Row
    data class Toggle(val id: Int, val title: String, val subtitle: String, val checked: Boolean) : Row
    data class Accent(val seeds: List<Int>, val selectedSeed: Int) : Row
    data class Opacity(val percent: Int) : Row
    data class ScopeHeader(val state: ScopeState) : Row
    data object Guide : Row
    data object About : Row
}
```

---
### `app/src/main/res/drawable/bg_badge.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/sable_surface_pressed" />
    <corners android:radius="100dp" />
    <stroke
        android:width="1dp"
        android:color="@color/sable_stroke" />
</shape>
```

---
### `app/src/main/res/drawable/bg_button.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="@color/sable_ripple">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/sable_accent" />
            <corners android:radius="16dp" />
        </shape>
    </item>
</ripple>
```

---
### `app/src/main/res/drawable/bg_button_outline.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="@color/sable_ripple">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/sable_surface" />
            <corners android:radius="16dp" />
            <stroke
                android:width="1dp"
                android:color="@color/sable_stroke_bright" />
        </shape>
    </item>
</ripple>
```

---
### `app/src/main/res/drawable/bg_card.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/sable_surface" />
    <corners android:radius="@dimen/sable_card_radius" />
    <stroke
        android:width="1dp"
        android:color="@color/sable_stroke" />
</shape>
```

---
### `app/src/main/res/drawable/bg_card_ripple.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     Tappable card: ripple clipped to the 22dp card radius. -->
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="@color/sable_ripple">
    <item android:id="@android:id/mask">
        <shape android:shape="rectangle">
            <solid android:color="#FFFFFFFF" />
            <corners android:radius="@dimen/sable_card_radius" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/sable_surface" />
            <corners android:radius="@dimen/sable_card_radius" />
            <stroke
                android:width="1dp"
                android:color="@color/sable_stroke" />
        </shape>
    </item>
</ripple>
```

---
### `app/src/main/res/drawable/bg_card_selected.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/sable_surface_pressed" />
    <corners android:radius="@dimen/sable_card_radius" />
    <stroke
        android:width="1dp"
        android:color="@color/sable_stroke_bright" />
</shape>
```

---
### `app/src/main/res/drawable/bg_dot.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/sable_active" />
    <size
        android:width="9dp"
        android:height="9dp" />
</shape>
```

---
### `app/src/main/res/drawable/bg_icon_frame.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     Rounded frame behind each app icon. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/sable_surface_pressed" />
    <corners android:radius="12dp" />
</shape>
```

---
### `app/src/main/res/drawable/bg_search.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/sable_surface_sunken" />
    <corners android:radius="16dp" />
    <stroke
        android:width="1dp"
        android:color="@color/sable_stroke" />
</shape>
```

---
### `app/src/main/res/drawable/bg_sheet.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/sable_surface" />
    <corners
        android:topLeftRadius="28dp"
        android:topRightRadius="28dp" />
    <stroke
        android:width="1dp"
        android:color="@color/sable_stroke" />
</shape>
```

---
### `app/src/main/res/drawable/bg_status_pill.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     Tinted at runtime from sable_active / sable_inactive. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#22FFFFFF" />
    <corners android:radius="100dp" />
    <stroke
        android:width="1dp"
        android:color="@color/sable_stroke_bright" />
</shape>
```

---
### `app/src/main/res/drawable/bg_step_number.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/sable_surface_pressed" />
    <stroke
        android:width="1dp"
        android:color="@color/sable_stroke_bright" />
    <size
        android:width="26dp"
        android:height="26dp" />
</shape>
```

---
### `app/src/main/res/drawable/bg_swatch.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     Palette swatch ring; the inner colour is set at runtime. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#FF000000" />
    <stroke
        android:width="1dp"
        android:color="@color/sable_stroke_bright" />
    <size
        android:width="28dp"
        android:height="28dp" />
</shape>
```

---
### `app/src/main/res/drawable/ic_launcher_background.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#000000" />
</shape>
```

---
### `app/src/main/res/drawable/ic_launcher_foreground.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     "Sable" glyph: a pure-white crescent moon (the dark) over a #EAEAEA
     baseline stroke, on true black. Adaptive, no raster assets. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Lit disc -->
    <path
        android:fillColor="#EAEAEA"
        android:fillAlpha="0.92"
        android:pathData="M58,42m-22,0a22,22 0,1 1,44 0a22,22 0,1 1,-44 0" />

    <!-- Punch-out: turns the disc into a crescent -->
    <path
        android:fillColor="#000000"
        android:pathData="M69,36m-20,0a20,20 0,1 1,40 0a20,20 0,1 1,-40 0" />

    <!-- Baseline -->
    <path
        android:fillColor="#EAEAEA"
        android:pathData="M37,70h34a3,3 0,0 1,3 3v0a3,3 0,0 1,-3 3h-34a3,3 0,0 1,-3 -3v0a3,3 0,0 1,3 -3z" />

    <!-- Sable accent underline -->
    <path
        android:fillColor="#3DD68C"
        android:pathData="M41.5,80h25a1.5,1.5 0,0 1,1.5 1.5v0a1.5,1.5 0,0 1,-1.5 1.5h-25a1.5,1.5 0,0 1,-1.5 -1.5v0a1.5,1.5 0,0 1,1.5 -1.5z" />
</vector>
```

---
### `app/src/main/res/layout/activity_main.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  SablePureBlack — by @drkmuqeett
  Companion UI shell: one flat RecyclerView plus a ModalBottomSheet layer for the
  per-app scope selector, so scrolling stays a single linear pass at 60fps+.
-->
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/rootLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/sable_black">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/contentList"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@color/sable_black"
        android:clipToPadding="false"
        android:overScrollMode="never"
        android:paddingStart="@dimen/sable_screen_padding"
        android:paddingTop="@dimen/sable_header_top"
        android:paddingEnd="@dimen/sable_screen_padding"
        android:paddingBottom="40dp"
        android:scrollbars="none"
        app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

---
### `app/src/main/res/layout/item_about.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="@dimen/sable_gap"
    android:background="@drawable/bg_card"
    android:orientation="vertical"
    android:padding="@dimen/sable_card_padding">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:fontFamily="sans-serif-medium"
            android:text="@string/about_title"
            android:textColor="@color/sable_text_primary"
            android:textSize="15sp" />

        <TextView
            android:id="@+id/aboutCreditBadge"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:background="@drawable/bg_badge"
            android:fontFamily="sans-serif-medium"
            android:paddingStart="10dp"
            android:paddingTop="4dp"
            android:paddingEnd="10dp"
            android:paddingBottom="4dp"
            android:text="@string/about_credit"
            android:textColor="@color/sable_text_secondary"
            android:textSize="11sp" />
    </LinearLayout>

    <TextView
        android:id="@+id/aboutBody"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="10dp"
        android:fontFamily="sans-serif"
        android:lineSpacingExtra="4dp"
        android:text="@string/about_body"
        android:textColor="@color/sable_text_secondary"
        android:textSize="13sp" />

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:layout_marginTop="14dp"
        android:background="@color/sable_stroke" />

    <TextView
        android:id="@+id/openAppInfo"
        android:layout_width="match_parent"
        android:layout_height="46dp"
        android:layout_marginTop="14dp"
        android:background="@drawable/bg_button_outline"
        android:clickable="true"
        android:focusable="true"
        android:fontFamily="sans-serif-medium"
        android:gravity="center"
        android:text="@string/open_app_info"
        android:textColor="@color/sable_text_primary"
        android:textSize="13sp" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:fontFamily="sans-serif"
        android:gravity="center"
        android:text="@string/footer_credit"
        android:textColor="@color/sable_text_tertiary"
        android:textSize="11sp"
        tools:text="SablePureBlack 2.0 · by @drkmuqeett" />

</LinearLayout>
```

---
### `app/src/main/res/layout/item_accent.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     Accent seed picker, shown when Custom Accent Dark is the active palette. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="@dimen/sable_gap"
    android:background="@drawable/bg_card"
    android:orientation="vertical"
    android:padding="@dimen/sable_card_padding">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:fontFamily="sans-serif-medium"
        android:text="@string/accent_picker_title"
        android:textColor="@color/sable_text_primary"
        android:textSize="14sp" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        android:fontFamily="sans-serif"
        android:text="@string/accent_picker_sub"
        android:textColor="@color/sable_text_tertiary"
        android:textSize="11sp" />

    <LinearLayout
        android:id="@+id/accentRow"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:orientation="horizontal" />

</LinearLayout>
```

---
### `app/src/main/res/layout/item_app.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     One selectable app in the scope sheet. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/appRow"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/selectableItemBackground"
    android:clickable="true"
    android:focusable="true"
    android:gravity="center_vertical"
    android:minHeight="60dp"
    android:orientation="horizontal"
    android:paddingStart="6dp"
    android:paddingTop="8dp"
    android:paddingEnd="6dp"
    android:paddingBottom="8dp">

    <FrameLayout
        android:layout_width="@dimen/sable_icon"
        android:layout_height="@dimen/sable_icon"
        android:layout_marginEnd="14dp"
        android:background="@drawable/bg_icon_frame"
        android:padding="6dp">

        <ImageView
            android:id="@+id/appIcon"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:contentDescription="@null"
            android:scaleType="fitCenter" />
    </FrameLayout>

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/appLabel"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:fontFamily="sans-serif-medium"
            android:maxLines="1"
            android:textColor="@color/sable_text_primary"
            android:textSize="14sp"
            tools:text="Chrome" />

        <TextView
            android:id="@+id/appPackage"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="1dp"
            android:ellipsize="end"
            android:fontFamily="sans-serif"
            android:maxLines="1"
            android:textColor="@color/sable_text_tertiary"
            android:textSize="10sp"
            tools:text="com.android.chrome" />
    </LinearLayout>

    <TextView
        android:id="@+id/appSystemBadge"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="6dp"
        android:background="@drawable/bg_badge"
        android:fontFamily="sans-serif-medium"
        android:letterSpacing="0.06"
        android:paddingStart="7dp"
        android:paddingTop="2dp"
        android:paddingEnd="7dp"
        android:paddingBottom="2dp"
        android:text="@string/scope_system_badge"
        android:textColor="@color/sable_text_secondary"
        android:textSize="8sp"
        android:visibility="gone"
        tools:visibility="visible" />

    <com.google.android.material.checkbox.MaterialCheckBox
        android:id="@+id/appCheck"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:clickable="false"
        android:focusable="false" />

</LinearLayout>
```

---
### `app/src/main/res/layout/item_guide.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="@dimen/sable_gap"
    android:background="@drawable/bg_card"
    android:orientation="vertical"
    android:padding="@dimen/sable_card_padding">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:fontFamily="sans-serif-medium"
        android:text="@string/section_guide"
        android:textColor="@color/sable_text_primary"
        android:textSize="15sp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="14dp"
        android:orientation="horizontal">

        <TextView
            android:layout_width="26dp"
            android:layout_height="26dp"
            android:background="@drawable/bg_step_number"
            android:fontFamily="sans-serif-medium"
            android:gravity="center"
            android:includeFontPadding="false"
            android:text="1"
            android:textColor="@color/sable_accent"
            android:textSize="11sp" />

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:layout_weight="1"
            android:fontFamily="sans-serif"
            android:lineSpacingExtra="3dp"
            android:text="@string/guide_step_1"
            android:textColor="@color/sable_text_secondary"
            android:textSize="13sp" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:orientation="horizontal">

        <TextView
            android:layout_width="26dp"
            android:layout_height="26dp"
            android:background="@drawable/bg_step_number"
            android:fontFamily="sans-serif-medium"
            android:gravity="center"
            android:includeFontPadding="false"
            android:text="2"
            android:textColor="@color/sable_accent"
            android:textSize="11sp" />

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:layout_weight="1"
            android:fontFamily="sans-serif"
            android:lineSpacingExtra="3dp"
            android:text="@string/guide_step_2"
            android:textColor="@color/sable_text_secondary"
            android:textSize="13sp" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:orientation="horizontal">

        <TextView
            android:layout_width="26dp"
            android:layout_height="26dp"
            android:background="@drawable/bg_step_number"
            android:fontFamily="sans-serif-medium"
            android:gravity="center"
            android:includeFontPadding="false"
            android:text="3"
            android:textColor="@color/sable_accent"
            android:textSize="11sp" />

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:layout_weight="1"
            android:fontFamily="sans-serif"
            android:lineSpacingExtra="3dp"
            android:text="@string/guide_step_3"
            android:textColor="@color/sable_text_secondary"
            android:textSize="13sp" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:orientation="horizontal">

        <TextView
            android:layout_width="26dp"
            android:layout_height="26dp"
            android:background="@drawable/bg_step_number"
            android:fontFamily="sans-serif-medium"
            android:gravity="center"
            android:includeFontPadding="false"
            android:text="4"
            android:textColor="@color/sable_accent"
            android:textSize="11sp" />

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:layout_weight="1"
            android:fontFamily="sans-serif"
            android:lineSpacingExtra="3dp"
            android:text="@string/guide_step_4"
            android:textColor="@color/sable_text_secondary"
            android:textSize="13sp" />
    </LinearLayout>

    <TextView
        android:id="@+id/openManagerButton"
        android:layout_width="match_parent"
        android:layout_height="52dp"
        android:layout_marginTop="18dp"
        android:background="@drawable/bg_button"
        android:clickable="true"
        android:focusable="true"
        android:fontFamily="sans-serif-medium"
        android:gravity="center"
        android:text="@string/open_manager"
        android:textColor="@color/sable_black"
        android:textSize="14sp" />

    <TextView
        android:id="@+id/scopeHint"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="10dp"
        android:fontFamily="sans-serif"
        android:gravity="center"
        android:lineSpacingExtra="2dp"
        android:text="@string/scope_hint"
        android:textColor="@color/sable_text_tertiary"
        android:textSize="11sp" />

</LinearLayout>
```

---
### `app/src/main/res/layout/item_header.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     Header banner: wordmark, author badge, live injection probe pill. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingTop="6dp"
    android:paddingBottom="20dp">

    <TextView
        android:id="@+id/headerTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:fontFamily="sans-serif-black"
        android:letterSpacing="-0.02"
        android:text="@string/app_name"
        android:textColor="@color/sable_text_primary"
        android:textSize="31sp" />

    <TextView
        android:id="@+id/headerTagline"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        android:fontFamily="sans-serif"
        android:text="@string/app_tagline"
        android:textColor="@color/sable_text_tertiary"
        android:textSize="12sp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:baselineAligned="false"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/authorBadge"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:background="@drawable/bg_badge"
            android:fontFamily="sans-serif-medium"
            android:letterSpacing="0.02"
            android:paddingStart="11dp"
            android:paddingTop="6dp"
            android:paddingEnd="11dp"
            android:paddingBottom="6dp"
            android:text="@string/author_badge"
            android:textColor="@color/sable_text_primary"
            android:textSize="11sp" />

        <LinearLayout
            android:id="@+id/statusPill"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:background="@drawable/bg_status_pill"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:paddingStart="10dp"
            android:paddingTop="6dp"
            android:paddingEnd="12dp"
            android:paddingBottom="6dp">

            <View
                android:id="@+id/statusDot"
                android:layout_width="9dp"
                android:layout_height="9dp"
                android:layout_marginEnd="7dp"
                android:background="@drawable/bg_dot" />

            <TextView
                android:id="@+id/statusText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:fontFamily="sans-serif-medium"
                android:textColor="@color/sable_active"
                android:textSize="11sp"
                tools:text="Active" />
        </LinearLayout>
    </LinearLayout>

    <TextView
        android:id="@+id/statusDetail"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="11dp"
        android:fontFamily="sans-serif"
        android:lineSpacingExtra="3dp"
        android:textColor="@color/sable_text_tertiary"
        android:textSize="12sp"
        tools:text="Framework injected via LSPosed Manager · Pitch Black palette live." />

    <TextView
        android:id="@+id/ipcBadge"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:fontFamily="sans-serif-medium"
        android:textColor="@color/sable_text_secondary"
        android:textSize="11sp"
        tools:text="IPC bridge healthy" />

</LinearLayout>
```

---
### `app/src/main/res/layout/item_opacity.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     Opacity slider card: how opaque forced surfaces become. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="@dimen/sable_gap"
    android:background="@drawable/bg_card"
    android:orientation="vertical"
    android:padding="@dimen/sable_card_padding">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:baselineAligned="false"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:fontFamily="sans-serif-medium"
            android:text="@string/opacity_title"
            android:textColor="@color/sable_text_primary"
            android:textSize="15sp" />

        <TextView
            android:id="@+id/opacityValue"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:fontFamily="sans-serif-medium"
            android:textColor="@color/sable_accent"
            android:textSize="14sp"
            tools:text="100%" />
    </LinearLayout>

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="3dp"
        android:fontFamily="sans-serif"
        android:lineSpacingExtra="2dp"
        android:text="@string/opacity_sub"
        android:textColor="@color/sable_text_tertiary"
        android:textSize="11sp" />

    <SeekBar
        android:id="@+id/opacitySeek"
        android:layout_width="match_parent"
        android:layout_height="40dp"
        android:layout_marginTop="4dp"
        android:max="100"
        android:paddingStart="0dp"
        android:paddingEnd="0dp"
        android:progressBackgroundTint="@color/sable_stroke_bright"
        android:progressTint="@color/sable_accent"
        android:splitTrack="false"
        android:thumbTint="@color/sable_accent"
        tools:progress="100" />

</LinearLayout>
```

---
### `app/src/main/res/layout/item_palette.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     One palette row: swatch, title, subtitle, selection ring. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/paletteRow"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="@dimen/sable_gap"
    android:background="@drawable/bg_card_ripple"
    android:clickable="true"
    android:focusable="true"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:padding="@dimen/sable_card_padding">

    <View
        android:id="@+id/paletteSwatch"
        android:layout_width="30dp"
        android:layout_height="30dp"
        android:layout_marginEnd="14dp"
        android:background="@drawable/bg_swatch" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/paletteTitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:fontFamily="sans-serif-medium"
            android:maxLines="1"
            android:textColor="@color/sable_text_primary"
            android:textSize="15sp"
            tools:text="Pitch Black" />

        <TextView
            android:id="@+id/paletteSubtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="3dp"
            android:ellipsize="end"
            android:fontFamily="sans-serif"
            android:maxLines="1"
            android:textColor="@color/sable_text_tertiary"
            android:textSize="11sp"
            tools:text="True #000000 — OLED pixels off" />
    </LinearLayout>

    <View
        android:id="@+id/paletteCheck"
        android:layout_width="22dp"
        android:layout_height="22dp"
        android:layout_marginStart="12dp"
        android:background="@drawable/bg_dot"
        android:visibility="invisible"
        tools:visibility="visible" />

</LinearLayout>
```

---
### `app/src/main/res/layout/item_scope_entry.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     Entry card in the main list that opens the scope selector sheet. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/scopeEntryRow"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="@dimen/sable_gap"
    android:background="@drawable/bg_card_ripple"
    android:clickable="true"
    android:focusable="true"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:padding="@dimen/sable_card_padding">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/scopeEntryTitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:fontFamily="sans-serif-medium"
            android:text="@string/section_scope"
            android:textColor="@color/sable_text_primary"
            android:textSize="15sp" />

        <TextView
            android:id="@+id/scopeEntrySummary"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="3dp"
            android:fontFamily="sans-serif"
            android:textColor="@color/sable_text_tertiary"
            android:textSize="11sp"
            tools:text="3 of 42 selected · only selected apps" />
    </LinearLayout>

    <TextView
        android:id="@+id/scopeEntryAction"
        android:layout_width="wrap_content"
        android:layout_height="36dp"
        android:background="@drawable/bg_button_outline"
        android:fontFamily="sans-serif-medium"
        android:gravity="center"
        android:paddingStart="16dp"
        android:paddingEnd="16dp"
        android:text="@string/scope_manage"
        android:textColor="@color/sable_text_primary"
        android:textSize="12sp" />

</LinearLayout>
```

---
### `app/src/main/res/layout/item_section_title.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/sectionTitle"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:fontFamily="sans-serif-medium"
    android:letterSpacing="0.08"
    android:paddingTop="10dp"
    android:paddingBottom="10dp"
    android:textAllCaps="true"
    android:textColor="@color/sable_text_tertiary"
    android:textSize="10sp"
    tools:text="Theme palette" />
```

---
### `app/src/main/res/layout/item_toggle.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/toggleRow"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="@dimen/sable_gap"
    android:background="@drawable/bg_card_ripple"
    android:clickable="true"
    android:focusable="true"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingStart="@dimen/sable_card_padding"
    android:paddingTop="14dp"
    android:paddingEnd="12dp"
    android:paddingBottom="14dp">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/toggleTitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:fontFamily="sans-serif-medium"
            android:textColor="@color/sable_text_primary"
            android:textSize="15sp"
            tools:text="WebView force-dark" />

        <TextView
            android:id="@+id/toggleSubtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="3dp"
            android:fontFamily="sans-serif"
            android:lineSpacingExtra="2dp"
            android:textColor="@color/sable_text_tertiary"
            android:textSize="11sp"
            tools:text="Force FORCE_DARK_ON in every WebView" />
    </LinearLayout>

    <com.google.android.material.materialswitch.MaterialSwitch
        android:id="@+id/toggleSwitch"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="10dp"
        android:clickable="false"
        android:focusable="false" />

</LinearLayout>
```

---
### `app/src/main/res/layout/sheet_scope.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  SablePureBlack — by @drkmuqeett
  Bottom sheet hosting the searchable per-app scope selector.
-->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/sheetRoot"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/bg_sheet"
    android:orientation="vertical"
    android:paddingStart="@dimen/sable_screen_padding"
    android:paddingEnd="@dimen/sable_screen_padding">

    <View
        android:layout_width="40dp"
        android:layout_height="4dp"
        android:layout_gravity="center_horizontal"
        android:layout_marginTop="10dp"
        android:background="@color/sable_stroke_bright" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="14dp"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:fontFamily="sans-serif-medium"
            android:text="@string/section_scope"
            android:textColor="@color/sable_text_primary"
            android:textSize="17sp" />

        <TextView
            android:id="@+id/sheetCount"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:fontFamily="sans-serif-medium"
            android:textColor="@color/sable_active"
            android:textSize="12sp"
            tools:text="3 of 42 selected" />
    </LinearLayout>

    <EditText
        android:id="@+id/searchField"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:layout_marginTop="12dp"
        android:background="@drawable/bg_search"
        android:fontFamily="sans-serif"
        android:hint="@string/scope_search_hint"
        android:imeOptions="actionSearch"
        android:importantForAutofill="no"
        android:inputType="text"
        android:maxLines="1"
        android:paddingStart="14dp"
        android:paddingEnd="14dp"
        android:singleLine="true"
        android:textColor="@color/sable_text_primary"
        android:textColorHint="@color/sable_text_tertiary"
        android:textSize="14sp" />

    <LinearLayout
        android:id="@+id/selectedModeRow"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="10dp"
        android:background="@drawable/bg_card_ripple"
        android:clickable="true"
        android:focusable="true"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="14dp"
        android:paddingTop="10dp"
        android:paddingEnd="10dp"
        android:paddingBottom="10dp">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:fontFamily="sans-serif-medium"
                android:text="@string/scope_selected_mode"
                android:textColor="@color/sable_text_primary"
                android:textSize="14sp" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="2dp"
                android:fontFamily="sans-serif"
                android:text="@string/scope_selected_mode_sub"
                android:textColor="@color/sable_text_tertiary"
                android:textSize="11sp" />
        </LinearLayout>

        <com.google.android.material.materialswitch.MaterialSwitch
            android:id="@+id/selectedModeSwitch"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:clickable="false"
            android:focusable="false" />
    </LinearLayout>

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="6dp"
        android:layout_weight="1">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/sheetList"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:overScrollMode="never"
            android:paddingBottom="18dp"
            android:scrollbars="none"
            app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager" />

        <LinearLayout
            android:id="@+id/sheetEmpty"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:orientation="vertical"
            android:visibility="gone"
            tools:visibility="visible">

            <TextView
                android:id="@+id/sheetEmptyText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:fontFamily="sans-serif"
                android:gravity="center"
                android:text="@string/scope_loading"
                android:textColor="@color/sable_text_tertiary"
                android:textSize="13sp" />
        </LinearLayout>
    </FrameLayout>

</LinearLayout>
```

---
### `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

---
### `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

---
### `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     Android 13+ themed icon. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

---
### `app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     Android 13+ themed icon. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

---
### `app/src/main/res/values/arrays.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     Apps pre-scoped on first install. Users freely add or remove entries in the
     manager; the payload re-reads the scope on every process start. -->
<resources>
    <string-array name="xposed_scope" translatable="false">
        <item>com.android.chrome</item>
        <item>com.google.android.gm</item>
        <item>com.google.android.apps.messaging</item>
        <item>com.google.android.youtube</item>
        <item>com.google.android.apps.maps</item>
        <item>com.whatsapp</item>
        <item>com.instagram.android</item>
        <item>org.telegram.messenger</item>
        <item>com.twitter.android</item>
        <item>com.reddit.frontpage</item>
    </string-array>
</resources>
```

---
### `app/src/main/res/values/colors.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<resources>
    <!-- True AMOLED pitch black: pixels physically off on OLED. -->
    <color name="sable_black">#FF000000</color>
    <!-- Deep Obsidian: soft dark contrast. -->
    <!-- Elevated card surface. -->
    <color name="sable_surface">#FF121212</color>
    <color name="sable_surface_pressed">#FF1A1A1A</color>
    <color name="sable_surface_sunken">#FF0D0D0D</color>
    <color name="sable_stroke">#FF242424</color>
    <color name="sable_stroke_bright">#FF3A3A3A</color>

    <color name="sable_text_primary">#FFFFFFFF</color>
    <color name="sable_text_secondary">#FFB3B3B3</color>
    <color name="sable_text_tertiary">#FF7A7A7A</color>

    <color name="sable_accent">#FFEAEAEA</color>
    <color name="sable_accent_dim">#FF9E9E9E</color>
    <color name="sable_accent_seed">#FF3DD68C</color>
    <color name="sable_active">#FF3DD68C</color>
    <color name="sable_inactive">#FFFF6B6B</color>
    <color name="sable_warning">#FFFFC857</color>

    <color name="sable_ripple">#33FFFFFF</color>
</resources>
```

---
### `app/src/main/res/values/dimens.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<resources>
    <dimen name="sable_screen_padding">20dp</dimen>
    <dimen name="sable_card_radius">22dp</dimen>
    <dimen name="sable_card_padding">18dp</dimen>
    <dimen name="sable_gap">12dp</dimen>
    <dimen name="sable_header_top">26dp</dimen>
    <dimen name="sable_icon">40dp</dimen>
</resources>
```

---
### `app/src/main/res/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<resources>
    <string name="app_name">SablePureBlack</string>
    <string name="app_tagline">Pitch-black AMOLED force for your apps</string>
    <string name="author_badge">by @drkmuqeett</string>
    <string name="xposed_description">by @drkmuqeett — SablePureBlack forces true pitch-black #000000 AMOLED panels and custom dark palettes on any selected app at runtime. No brightness changes. No screen filters.</string>

    <!-- Header / probe -->
    <string name="status_active">Active</string>
    <string name="status_inactive">Not Activated in LSPosed</string>
    <string name="probe_active_detail">Framework injected via %1$s · %2$s palette live in every scoped app.</string>
    <string name="probe_inactive_detail">Not injected yet — enable the module in %1$s, scope your apps, then force-stop them.</string>
    <string name="probe_ipc_ok">IPC bridge healthy</string>
    <string name="probe_ipc_missing">IPC bridge unavailable</string>

    <!-- Sections -->
    <string name="section_palette">Theme palette</string>
    <string name="section_toggles">Overrides</string>
    <string name="section_scope">Per-app scope</string>
    <string name="section_guide">Setup guide</string>

    <!-- Palette switcher -->
    <string name="palette_pitch_black">Pitch Black</string>
    <string name="palette_pitch_black_sub">True #000000 — OLED pixels off</string>
    <string name="palette_obsidian">Deep Obsidian</string>
    <string name="palette_obsidian_sub">Soft dark contrast, #0A0A0A surfaces</string>
    <string name="palette_custom">Custom Accent Dark</string>
    <string name="palette_custom_sub">Your accent, clamped to near-black</string>
    <string name="accent_picker_title">Accent seed</string>
    <string name="accent_picker_sub">Tints the near-black surface and UI accents. Every channel stays inside the AMOLED-safe band.</string>

    <!-- Toggles -->
    <string name="toggle_webview">WebView force-dark</string>
    <string name="toggle_webview_sub">Force FORCE_DARK_ON in every WebView</string>
    <string name="toggle_text">Smart text contrast boost</string>
    <string name="toggle_text_sub">Lift unreadable dark text to #EAEAEA</string>
    <string name="opacity_title">Forced surface opacity</string>
    <string name="opacity_sub">Translucent white cards become translucent black, never solid slabs</string>
    <string name="opacity_value">%1$d%%</string>

    <!-- Scope selector -->
    <string name="scope_search_hint">Search apps</string>
    <string name="scope_selected_mode">Only selected apps</string>
    <string name="scope_selected_mode_sub">When off, every app in the LSPosed scope is forced black</string>
    <string name="scope_count">%1$d of %2$d selected</string>
    <string name="scope_loading">Loading installed apps…</string>
    <string name="scope_empty">No apps match “%1$s”</string>
    <string name="scope_system_badge">SYSTEM</string>
    <string name="scope_manage">Manage</string>
    <string name="scope_entry_summary">%1$d of %2$d selected · %3$s</string>
    <string name="scope_hint">Force-stop a selected app after toggling so hooks attach immediately.</string>

    <!-- Guide -->
    <string name="guide_step_1">Enable SablePureBlack in LSPosed Manager (or Zygisk Vector).</string>
    <string name="guide_step_2">Open Scope and tick the apps you want forced black, or use the list above.</string>
    <string name="guide_step_3">Force-stop each ticked app from App info — hooks attach on its next launch.</string>
    <string name="guide_step_4">Reopen the app. Light surfaces become your chosen palette instantly.</string>
    <string name="open_manager">Open LSPosed scope settings</string>
    <string name="open_app_info">Open App info</string>

    <!-- About -->
    <string name="about_title">About</string>
    <string name="about_credit">by @drkmuqeett</string>
    <string name="about_body">SablePureBlack rewrites light window and view backgrounds to a true AMOLED palette at runtime, inside each hooked app process. It never writes Settings.System.SCREEN_BRIGHTNESS, never changes brightness mode, never sets window screenBrightness and never applies a SurfaceFlinger colour matrix or hardware tint.</string>
    <string name="footer_credit">SablePureBlack 2.0 · by @drkmuqeett</string>

    <!-- Toasts -->
    <string name="toast_saved">Saved — force-stop the app to apply</string>
    <string name="toast_manager_missing">No LSPosed / Vector manager found on this device</string>
    <string name="toast_app_not_found">Could not open app info</string>
</resources>
```

---
### `app/src/main/res/values/themes.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett
     Material 3, dark-only, true #000000. Material3 themes descend from
     Theme.AppCompat, so AppCompatActivity and MaterialSwitch both inflate
     correctly. -->
<resources xmlns:tools="http://schemas.android.com/tools">

    <style name="Theme.SablePureBlack" parent="Theme.Material3.Dark.NoActionBar">

        <!-- Window -->
        <item name="android:colorBackground">@color/sable_black</item>
        <item name="android:windowBackground">@color/sable_black</item>
        <item name="android:statusBarColor">@color/sable_black</item>
        <item name="android:navigationBarColor">@color/sable_black</item>
        <item name="android:windowDrawsSystemBarBackgrounds">true</item>
        <item name="android:windowLightStatusBar" tools:targetApi="23">false</item>
        <item name="android:windowLightNavigationBar" tools:targetApi="27">false</item>
        <item name="android:enforceStatusBarContrast" tools:targetApi="29">false</item>
        <item name="android:enforceNavigationBarContrast" tools:targetApi="29">false</item>
        <item name="android:windowLayoutInDisplayCutoutMode" tools:targetApi="27">shortEdges</item>

        <!-- Material 3 colour roles, pinned so dynamic colour can never lighten
             the UI past true black. -->
        <item name="colorPrimary">@color/sable_accent</item>
        <item name="colorOnPrimary">@color/sable_black</item>
        <item name="colorPrimaryContainer">@color/sable_surface</item>
        <item name="colorOnPrimaryContainer">@color/sable_text_primary</item>
        <item name="colorSecondary">@color/sable_accent_seed</item>
        <item name="colorOnSecondary">@color/sable_black</item>
        <item name="colorSecondaryContainer">@color/sable_surface_pressed</item>
        <item name="colorOnSecondaryContainer">@color/sable_text_primary</item>
        <item name="colorTertiary">@color/sable_accent_dim</item>
        <item name="colorSurface">@color/sable_surface</item>
        <item name="colorOnSurface">@color/sable_text_primary</item>
        <item name="colorSurfaceVariant">@color/sable_surface_pressed</item>
        <item name="colorOnSurfaceVariant">@color/sable_text_secondary</item>
        <item name="colorOutline">@color/sable_stroke</item>
        <item name="colorOutlineVariant">@color/sable_stroke</item>
        <item name="colorControlActivated">@color/sable_accent</item>
        <item name="colorControlNormal">@color/sable_accent_dim</item>
        <item name="colorError">@color/sable_inactive</item>
        <item name="android:colorAccent">@color/sable_accent</item>
        <item name="android:textColorPrimary">@color/sable_text_primary</item>
        <item name="android:textColorSecondary">@color/sable_text_secondary</item>
        <item name="android:textColorHint">@color/sable_text_tertiary</item>
    </style>

</resources>
```

---
### `app/src/main/res/xml/backup_rules.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<full-backup-content>
    <include
        domain="sharedpref"
        path="." />
</full-backup-content>
```

---
### `app/src/main/res/xml/data_extraction_rules.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SablePureBlack — by @drkmuqeett -->
<data-extraction-rules>
    <cloud-backup>
        <include
            domain="sharedpref"
            path="." />
    </cloud-backup>
    <device-transfer>
        <include
            domain="sharedpref"
            path="." />
    </device-transfer>
</data-extraction-rules>
```

---
### `docs/ui-preview.png`

Binary file (231,238 bytes) — the standard Gradle wrapper jar, not inlined here; it is committed in the repo at that path.

---
### `gradle/wrapper/gradle-wrapper.jar`

Binary file (48,749 bytes) — the standard Gradle wrapper jar, not inlined here; it is committed in the repo at that path.

---
