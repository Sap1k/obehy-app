import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Compose for an Android application or library module (apply after either). Material 3
 * Expressive is opted into here, once, so no source file needs an @OptIn for it.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.findByType<ApplicationExtension>()?.buildFeatures?.compose = true
            extensions.findByType<LibraryExtension>()?.buildFeatures?.compose = true
            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions.optIn.addAll(
                    "androidx.compose.material3.ExperimentalMaterial3Api",
                    "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
                )
            }
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
