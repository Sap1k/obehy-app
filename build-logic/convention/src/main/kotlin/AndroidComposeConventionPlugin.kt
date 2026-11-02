import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType

/** Compose for an Android application or library module (apply after either). */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.findByType<ApplicationExtension>()?.buildFeatures?.compose = true
            extensions.findByType<LibraryExtension>()?.buildFeatures?.compose = true
            dependencies {
                val bom = platform(library("androidx-compose-bom"))
                "implementation"(bom)
                "implementation"(library("androidx-compose-ui"))
                "implementation"(library("androidx-compose-material3"))
                "implementation"(library("androidx-compose-material-icons-extended"))
                "implementation"(library("androidx-compose-ui-tooling-preview"))
                "debugImplementation"(library("androidx-compose-ui-tooling"))
            }
        }
    }
}
