import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

// Android Lint checks that keep styling in core:ui. Applied to every Android module except
// core:ui itself by the convention plugins (lintChecks), so `lintDebug` enforces them.
plugins {
    alias(libs.plugins.obehy.jvm.library)
}

kotlin {
    compilerOptions {
        // Lint loads these classes next to its own Kotlin runtime (2.2 for lint 32.4).
        apiVersion = KotlinVersion.KOTLIN_2_2
        languageVersion = KotlinVersion.KOTLIN_2_2
    }
}

dependencies {
    compileOnly(libs.lint.api)
    compileOnly(libs.lint.checks)
    testImplementation(libs.lint.api)
    testImplementation(libs.lint.checks)
    testImplementation(libs.lint.tests)
}

tasks.jar {
    manifest {
        attributes("Lint-Registry-v2" to "cz.obehy.lint.ObehyIssueRegistry")
    }
}
