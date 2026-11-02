plugins {
    alias(libs.plugins.obehy.android.application)
    alias(libs.plugins.obehy.android.compose)
    alias(libs.plugins.obehy.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "cz.obehy.app"

    defaultConfig {
        applicationId = "cz.obehy.app"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        // Only the languages the app is translated into (per-app language setting).
        localeFilters += listOf("en", "cs")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.ui)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.planner)
    implementation(projects.feature.trip)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.kotlinx.serialization.json)
}
