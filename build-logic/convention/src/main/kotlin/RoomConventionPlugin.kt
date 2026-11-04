import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Room through KSP, with its schemas kept in the module (apply after the Android plugin). */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("androidx.room")
            extensions.configure<RoomExtension> { schemaDirectory("$projectDir/schemas") }
            dependencies {
                "implementation"(library("androidx-room-runtime"))
                "ksp"(library("androidx-room-compiler"))
            }
        }
    }
}
