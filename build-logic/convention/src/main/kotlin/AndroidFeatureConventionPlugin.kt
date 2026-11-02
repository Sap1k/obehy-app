import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * A feature module: an Android library with Compose, Hilt view models and type-safe navigation
 * routes. Features depend on the core modules, never on each other.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("obehy.android.library")
            pluginManager.apply("obehy.android.compose")
            pluginManager.apply("obehy.hilt")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            dependencies {
                "implementation"(project(":core:model"))
                "implementation"(project(":core:data"))
                "implementation"(project(":core:ui"))
                "implementation"(library("androidx-lifecycle-runtime-compose"))
                "implementation"(library("androidx-lifecycle-viewmodel-compose"))
                "implementation"(library("androidx-hilt-lifecycle-viewmodel-compose"))
                "implementation"(library("androidx-navigation-compose"))
                "implementation"(library("kotlinx-serialization-json"))
                "testImplementation"(library("kotlinx-coroutines-test"))
            }
        }
    }
}
