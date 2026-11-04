plugins {
    alias(libs.plugins.obehy.android.feature)
}

android {
    namespace = "cz.obehy.feature.trip"
}

dependencies {
    implementation(project(":core:map"))
}
