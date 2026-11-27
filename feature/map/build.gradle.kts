plugins {
    alias(libs.plugins.obehy.android.feature)
}

android {
    namespace = "cz.obehy.feature.map"
}

dependencies {
    implementation(project(":core:map"))
}
