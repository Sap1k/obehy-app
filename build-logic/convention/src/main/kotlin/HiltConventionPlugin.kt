import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Hilt through KSP, for an Android module (apply after the Android plugin). */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")
            dependencies {
                "implementation"(library("hilt-android"))
                "ksp"(library("hilt-compiler"))
            }
        }
    }
}
