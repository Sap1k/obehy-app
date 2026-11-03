plugins {
    alias(libs.plugins.obehy.android.library)
    alias(libs.plugins.obehy.android.compose)
}

android {
    namespace = "cz.obehy.core.ui"
}

dependencies {
    api(project(":core:model"))
}
