plugins {
    alias(libs.plugins.obehy.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
