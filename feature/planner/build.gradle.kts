plugins {
    alias(libs.plugins.obehy.android.feature)
}

android {
    namespace = "cz.obehy.feature.planner"
}

dependencies {
    implementation(project(":core:map"))
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.datastore.preferences)
}
