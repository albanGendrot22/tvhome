import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Réglages personnels (jeton TMDB, coordonnées météo) : dans local.properties, jamais dans le code ni sur GitHub
val perso = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "fr.alban.tvhome"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.alban.tvhome"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        // weather.lat / weather.lon : position de la météo (par défaut Paris). tmdb.token : jeton de lecture TMDB
        // (vide = pas de Top Shelf « tendances »).
        buildConfigField("double", "WEATHER_LAT", perso.getProperty("weather.lat", "48.8566"))
        buildConfigField("double", "WEATHER_LON", perso.getProperty("weather.lon", "2.3522"))
        buildConfigField("String", "TMDB_TOKEN", "\"${perso.getProperty("tmdb.token", "")}\"")
    }

    buildTypes {
        release {
            // Compose est lent en debug : sur une petite TV, on teste en release.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
