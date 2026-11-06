package cz.obehy.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import org.junit.Test

class DesignSystemDetectorTest {
    @Test
    fun `theme values, tokens, zero and resources pass`() {
        lint()
            .files(
                *stubs,
                kotlin(
                    """
                package cz.obehy.feature.x

                import androidx.compose.material3.Icon
                import androidx.compose.material3.Text
                import androidx.compose.ui.graphics.Color
                import androidx.compose.ui.unit.dp

                fun Screen(spacing: androidx.compose.ui.unit.Dp, label: String) {
                    val none = 0.dp
                    val clear = Color.Transparent
                    Text(label)
                    Text(text = label)
                    Icon(Any(), contentDescription = null)
                    Icon(Any(), contentDescription = label)
                }
                """,
                ).indented(),
            ).check()
            .expectClean()
    }

    @Test
    fun `literals for colour, type, size, shape and text are errors`() {
        lint()
            .files(
                *stubs,
                kotlin(
                    """
                package cz.obehy.feature.x

                import androidx.compose.foundation.shape.CircleShape
                import androidx.compose.foundation.shape.RoundedCornerShape
                import androidx.compose.material3.Icon
                import androidx.compose.material3.Text
                import androidx.compose.ui.graphics.Color
                import androidx.compose.ui.text.TextStyle
                import androidx.compose.ui.text.font.FontFamily
                import androidx.compose.ui.unit.dp
                import androidx.compose.ui.unit.sp

                fun Screen() {
                    val gap = 16.dp
                    val red = Color.Red
                    val brand = Color(0xFF246568)
                    val size = 12.sp
                    val style = TextStyle()
                    val font = FontFamily()
                    val rounded = RoundedCornerShape(8.dp)
                    val circle = CircleShape
                    Text("Hello")
                    Icon(Any(), contentDescription = "Back")
                }
                """,
                ).indented(),
            ).check()
            .expectErrorCount(11)
            .expectContains("[ObehyRawDimension]")
            .expectContains("[ObehyRawColor]")
            .expectContains("[ObehyRawTypography]")
            .expectContains("[ObehyRawShape]")
            .expectContains("[ObehyHardcodedText]")
    }

    @Test
    fun `only buttons that morph pass`() {
        lint()
            .files(
                *stubs,
                kotlin(
                    """
                package cz.obehy.feature.x

                import androidx.compose.material3.Button
                import androidx.compose.material3.ButtonShapes

                fun Screen(shapes: ButtonShapes) {
                    Button(onClick = {}, shapes = shapes)
                    Button(onClick = {})
                }
                """,
                ).indented(),
            ).check()
            .expectErrorCount(1)
            .expectContains("[ObehyStaticButton]")
    }

    // Aliasing an import of CircleShape or FontFamily (only) hides it from the check; nobody does that.
    private fun TestLintTask.check() =
        issues(*DesignSystemDetector.ISSUES.toTypedArray())
            .allowMissingSdk()
            .skipTestModes(TestMode.IMPORT_ALIAS)
            .run()

    // Minimal stand-ins for the Compose APIs the checks look for, in their real packages and files.
    private val stubs: Array<TestFile> =
        arrayOf(
            kotlin(
                "src/androidx/compose/ui/unit/Dp.kt",
                """
            package androidx.compose.ui.unit
            class Dp(val value: Float)
            val Int.dp: Dp get() = Dp(toFloat())
            """,
            ).indented(),
            kotlin(
                "src/androidx/compose/ui/unit/TextUnit.kt",
                """
            package androidx.compose.ui.unit
            class TextUnit(val value: Float)
            val Int.sp: TextUnit get() = TextUnit(toFloat())
            """,
            ).indented(),
            kotlin(
                "src/androidx/compose/ui/graphics/Color.kt",
                """
            package androidx.compose.ui.graphics
            class Color(val value: Float) {
                companion object {
                    val Red = Color(1f)
                    val Transparent = Color(0f)
                }
            }
            fun Color(color: Long): Color = Color(color.toFloat())
            """,
            ).indented(),
            kotlin(
                "src/androidx/compose/ui/text/TextStyle.kt",
                """
            package androidx.compose.ui.text
            class TextStyle(val fontSize: Any? = null)
            """,
            ).indented(),
            kotlin(
                "src/androidx/compose/ui/text/font/FontFamily.kt",
                """
            package androidx.compose.ui.text.font
            abstract class FontFamily
            fun FontFamily(vararg fonts: Any): FontFamily = TODO()
            """,
            ).indented(),
            kotlin(
                "src/androidx/compose/foundation/shape/RoundedCornerShape.kt",
                """
            package androidx.compose.foundation.shape
            class Shape
            fun RoundedCornerShape(size: androidx.compose.ui.unit.Dp): Shape = Shape()
            val CircleShape: Shape = Shape()
            """,
            ).indented(),
            kotlin(
                "src/androidx/compose/material3/Text.kt",
                """
            package androidx.compose.material3
            fun Text(text: String, modifier: Any? = null) {}
            """,
            ).indented(),
            kotlin(
                "src/androidx/compose/material3/Button.kt",
                """
            package androidx.compose.material3
            class ButtonShapes
            fun Button(onClick: () -> Unit, modifier: Any? = null, shape: Any? = null) {}
            fun Button(onClick: () -> Unit, shapes: ButtonShapes, modifier: Any? = null) {}
            """,
            ).indented(),
            kotlin(
                "src/androidx/compose/material3/Icon.kt",
                """
            package androidx.compose.material3
            fun Icon(imageVector: Any, contentDescription: String?, modifier: Any? = null) {}
            """,
            ).indented(),
        )
}
