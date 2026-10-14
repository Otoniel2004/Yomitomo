plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.otoniel2004.mangareaderjp"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.otoniel2004.mangareaderjp"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
    }

    // Release signing key: kept OUTSIDE the repository. Where it is and its passwords come from
    // Gradle properties in ~/.gradle/gradle.properties or from environment variables.
    // Without them, release builds fall back to the debug key (fine for local testing only).
    signingConfigs {
        create("release") {
            releaseSigning("keystore", "MANGA_READER_KEYSTORE")?.let { storeFile = file(it) }
            storePassword = releaseSigning("keystorePassword", "MANGA_READER_KEYSTORE_PASSWORD")
            keyAlias = releaseSigning("keyAlias", "MANGA_READER_KEY_ALIAS")
            keyPassword = releaseSigning("keyPassword", "MANGA_READER_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val hasReleaseKey = releaseSigning("keystore", "MANGA_READER_KEYSTORE") != null
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            // Both Kuromoji jars ship the same docs; they're credited in the README instead.
            excludes += setOf("META-INF/CONTRIBUTORS.md", "META-INF/LICENSE.md", "META-INF/NOTICE.md")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.text.japanese)
    implementation(libs.mlkit.translate)
    implementation(libs.kuromoji.ipadic)

    testImplementation(libs.junit)
}

// The bundled dictionary is generated (not versioned): build it before the app when missing.
tasks.named("preBuild") {
    dependsOn(":tools:dictbuilder:buildDictionary")
}

/** A release-signing value from ~/.gradle/gradle.properties (mangaReader.<name>) or an environment variable. */
fun releaseSigning(name: String, env: String): String? =
    providers.gradleProperty("mangaReader.$name").orNull ?: providers.environmentVariable(env).orNull
