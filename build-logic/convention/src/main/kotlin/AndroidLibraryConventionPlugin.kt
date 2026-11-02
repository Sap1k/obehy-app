import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                compileSdk = sdkLevel("compileSdk")
                defaultConfig {
                    minSdk = sdkLevel("minSdk")
                }
                compileOptions {
                    sourceCompatibility = JAVA_VERSION
                    targetCompatibility = JAVA_VERSION
                }
                testOptions.unitTests.isReturnDefaultValues = true
            }
            configureKotlin()
            addDesignSystemLint()
            dependencies {
                "testImplementation"(library("junit"))
            }
        }
    }
}
