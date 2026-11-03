plugins {
    alias(libs.plugins.obehy.android.library)
    alias(libs.plugins.obehy.android.compose)
}

android {
    namespace = "cz.obehy.core.map"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(libs.maplibre.compose)
    // The native renderer. OpenGL, because maplibre-compose 0.20.0 doesn't ship the bridge its
    // Vulkan path loads (libmaplibre_compose_vulkan.so): with the Vulkan runtime the map stays empty.
    runtimeOnly(libs.maplibre.native.runtime.opengl)
    implementation(libs.kotlinx.serialization.json)
}
