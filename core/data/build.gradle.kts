plugins {
    alias(libs.plugins.obehy.android.library)
    alias(libs.plugins.obehy.hilt)
}

android {
    namespace = "cz.obehy.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
