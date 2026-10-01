import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

// ---------------------------------------------------------------------------
// Git-based auto-versioning — no manual bumps.
//   versionCode = total commit count (grows with every commit)
//   versionName = highest vX.Y.Z tag: clean "X.Y.Z" when built on the tag,
//                 "X.Y.Z-N-g<hash>" for commits after it,
//                 "1.5.0-N-g<hash>" while no v* tag exists yet.
//   Release flow: tag a commit (git tag v1.5.0 && git push --tags) and every
//   build of it — local or CI — reports that version.
//   Override a build with:  ./gradlew assembleRelease -PappVersionName=2.0.0 -PappVersionCode=99
//   Inspect with:           ./gradlew showVersion
// ---------------------------------------------------------------------------
data class AutoVersion(val name: String, val code: Int)

/** Configuration-cache-safe: git runs inside a ValueSource, not on the script. */
abstract class GitVersionSource : ValueSource<AutoVersion, ValueSourceParameters.None> {

    override fun obtain(): AutoVersion {
        val latest = runGit("tag", "--list", "v*", "--sort=-v:refname")
            ?.lineSequence()?.firstOrNull { it.isNotBlank() }
        val commits = runGit("rev-list", "--count", "HEAD")?.toIntOrNull()
        val hash = runGit("rev-parse", "--short", "HEAD") ?: "unknown"
        val since = latest?.let { runGit("rev-list", "--count", "$it..HEAD")?.toIntOrNull() } ?: 0
        val base = latest?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE

        val name = when {
            latest == null && commits != null -> "$base-$commits-g$hash"
            since == 0 -> base
            else -> "$base-$since-g$hash"
        }
        return AutoVersion(name, commits ?: 1)
    }

    private fun runGit(vararg args: String): String? = runCatching {
        ProcessBuilder("git", *args)
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readText()
            .trim()
            .takeIf { it.isNotEmpty() }
    }.getOrNull()

    private companion object {
        const val DEFAULT_BASE = "1.5.0"
    }
}

val autoVersion: AutoVersion by lazy {
    val computed = providers.of(GitVersionSource::class.java) { }.get()
    AutoVersion(
        name = providers.gradleProperty("appVersionName").orNull ?: computed.name,
        code = providers.gradleProperty("appVersionCode").orNull?.toIntOrNull() ?: computed.code,
    )
}

tasks.register("showVersion") {
    val computedName = autoVersion.name
    val computedCode = autoVersion.code
    doLast { println("versionName=$computedName versionCode=$computedCode") }
}

android {
    namespace = "com.mohammed.mosa.qrscanner"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mohammed.mosa.qrscanner"
        minSdk = 24
        targetSdk = 37
        versionCode = autoVersion.code
        versionName = autoVersion.name

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            // Strip unused resources from the release APK.

        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.compose.material:material-icons-extended")


    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // On-device QR detection (works offline)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // System splash screen (backported to API 24)
    implementation("androidx.core:core-splashscreen:1.2.0")
    // Room
    val room = "2.8.5"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Barcode generation (all formats, offline)
    implementation("com.google.zxing:core:3.5.3")
    implementation("dev.chrisbanes.haze:haze:1.6.0")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}