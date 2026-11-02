import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.library(alias: String): Provider<MinimalExternalModuleDependency> =
    libs.findLibrary(alias).orElseThrow { IllegalArgumentException("No library $alias") }

internal fun Project.sdkLevel(name: String): Int =
    libs.findVersion(name).orElseThrow().requiredVersion.toInt()

internal val JAVA_VERSION = JavaVersion.VERSION_17

/** Every Kotlin compilation targets the same JVM bytecode as Java. */
internal fun Project.configureKotlin() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
}
