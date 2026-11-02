// Plugin versions for every module come from here; modules apply the convention plugins
// (build-logic/convention), which configure them.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.spotless)
}

// One formatter for the whole repository: ktlint with the Compose rules (io.nlopez.compose.rules),
// configured in .editorconfig. `./gradlew spotlessApply` fixes what it can; CI runs spotlessCheck.
spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    val ktlintVersion = libs.versions.ktlint.get()
    val composeRules =
        libs.compose.rules.ktlint
            .get()
            .toString()
    kotlin {
        target("**/src/**/*.kt", "build-logic/**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion).customRuleSets(listOf(composeRules))
    }
    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
}
