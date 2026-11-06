package cz.obehy.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.skipParenthesizedExprUp

/**
 * Keeps styling in core:ui: outside it, colours, type, dimensions, shapes and user-visible text
 * come from the theme, the tokens and the string resources, never from literals. core:ui, where
 * those are defined, does not run these checks.
 */
class DesignSystemDetector :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableReferenceNames(): List<String> = listOf("dp", "sp", "CircleShape") + NAMED_COLORS

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        val member = referenced as? PsiMember ?: return
        val owner = member.containingClass?.qualifiedName ?: return
        // The Kotlin property behind the getter: getRed-0d7_KjU (mangled for the value class) is Red.
        val name =
            member.name
                .orEmpty()
                .substringBefore(
                    '-',
                ).removePrefix("get")
                .replaceFirstChar { it.uppercaseChar() }
        when {
            name == "Dp" && owner == "androidx.compose.ui.unit.DpKt" -> {
                if (receiverOf(reference)?.evaluate().isZero()) return
                report(
                    context,
                    reference,
                    RAW_DIMENSION,
                    "A raw dp value: use `ObehyTheme.spacing` / `ObehyTheme.sizes`, or a core:ui component",
                )
            }

            name == "Sp" && owner == "androidx.compose.ui.unit.TextUnitKt" -> {
                report(context, reference, RAW_TYPOGRAPHY, "A raw sp value: use a `MaterialTheme.typography` style")
            }

            name == "CircleShape" && owner.startsWith("androidx.compose.foundation.shape.") -> {
                report(
                    context,
                    reference,
                    RAW_SHAPE,
                    "A hand-made shape: use `MaterialTheme.shapes`, `MaterialShapes` or the component's default",
                )
            }

            name in NAMED_COLORS && owner.removeSuffix(".Companion") == "androidx.compose.ui.graphics.Color" -> {
                report(context, reference, RAW_COLOR, "A fixed colour: use a `MaterialTheme.colorScheme` role")
            }
        }
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf("Color", "FontFamily", "RoundedCornerShape", "CutCornerShape", "Text") + BUTTONS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val owner = method.containingClass?.qualifiedName ?: return
        when {
            owner == "androidx.compose.ui.graphics.ColorKt" -> {
                report(context, node, RAW_COLOR, "A fixed colour: use a `MaterialTheme.colorScheme` role")
            }

            owner.startsWith("androidx.compose.ui.text.font.FontFamily") -> {
                report(context, node, RAW_TYPOGRAPHY, "A font set by hand: use a `MaterialTheme.typography` style")
            }

            owner.startsWith("androidx.compose.foundation.shape.") -> {
                report(
                    context,
                    node,
                    RAW_SHAPE,
                    "A hand-made shape: use `MaterialTheme.shapes`, `MaterialShapes` or the component's default",
                )
            }

            owner.startsWith("androidx.compose.material3.") && method.name in BUTTONS -> {
                if (node.valueArguments.none { it.getExpressionType()?.canonicalText?.endsWith("Shapes") == true }) {
                    report(
                        context,
                        node,
                        STATIC_BUTTON,
                        "A button that doesn't react to the press: pass `shapes`, such as `ButtonDefaults.shapes()`",
                    )
                }
            }

            owner.startsWith("androidx.compose.material3.") && method.name.substringBefore('-') == "Text" -> {
                val text =
                    context.evaluator
                        .computeArgumentMapping(node, method)
                        .entries
                        .firstOrNull { it.value.name == "text" }
                        ?.key
                if (text != null && text.isLiteralText()) {
                    report(
                        context,
                        text,
                        HARDCODED_TEXT,
                        "Text shown to people comes from `stringResource(R.string…)`, in English and Czech",
                    )
                }
            }
        }
    }

    override fun getApplicableConstructorTypes(): List<String> =
        listOf("androidx.compose.ui.text.TextStyle", "androidx.compose.ui.text.font.FontFamily")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        report(context, node, RAW_TYPOGRAPHY, "Text styling built by hand: use a `MaterialTheme.typography` style")
    }

    // contentDescription is a parameter of many composables (Icon, Image, …): check every call.
    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.valueArgumentCount == 0) return
                val method = node.resolve() ?: return
                if (method.parameterList.parameters.none { it.name == "contentDescription" }) return
                val description =
                    context.evaluator
                        .computeArgumentMapping(node, method)
                        .entries
                        .firstOrNull { it.value.name == "contentDescription" }
                        ?.key ?: return
                if (description.isLiteralText()) {
                    report(
                        context,
                        description,
                        HARDCODED_TEXT,
                        "Text read to people comes from `stringResource(R.string…)`, in English and Czech",
                    )
                }
            }
        }

    private fun receiverOf(reference: UReferenceExpression): UExpression? =
        (skipParenthesizedExprUp(reference.uastParent) as? UQualifiedReferenceExpression)
            ?.takeIf { it.selector == reference || it == reference }
            ?.receiver

    private fun Any?.isZero() = (this as? Number)?.toDouble() == 0.0

    private fun UExpression.isLiteralText() = (evaluate() as? String)?.isNotBlank() == true

    private fun report(
        context: JavaContext,
        node: UElement,
        issue: Issue,
        message: String,
    ) {
        context.report(issue, node, context.getLocation(node), message)
    }

    companion object {
        private val NAMED_COLORS =
            listOf(
                "Black",
                "DarkGray",
                "Gray",
                "LightGray",
                "White",
                "Red",
                "Green",
                "Blue",
                "Yellow",
                "Cyan",
                "Magenta",
            )

        // Transparent and Unspecified are left out on purpose: "no colour" and "inherit".

        private val BUTTONS =
            listOf(
                "Button",
                "ElevatedButton",
                "FilledTonalButton",
                "OutlinedButton",
                "TextButton",
                "IconButton",
                "FilledIconButton",
                "FilledTonalIconButton",
                "OutlinedIconButton",
                "IconToggleButton",
                "FilledIconToggleButton",
                "FilledTonalIconToggleButton",
                "OutlinedIconToggleButton",
            )

        private fun issue(
            id: String,
            brief: String,
            explanation: String,
        ) = Issue.create(
            id = id,
            briefDescription = brief,
            explanation =
                "$explanation See CLAUDE.md, \"UI rules\". In the rare real exception, " +
                    "suppress it with `@SuppressLint(\"$id\")` and a comment saying why.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(DesignSystemDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )

        val RAW_COLOR =
            issue(
                "ObehyRawColor",
                "Colour outside the theme",
                "Colours come from `MaterialTheme.colorScheme` (defined in core:ui Theme.kt), so light and dark " +
                    "mode and the brand stay consistent.",
            )
        val RAW_TYPOGRAPHY =
            issue(
                "ObehyRawTypography",
                "Text styling outside the theme",
                "Text styles come from `MaterialTheme.typography` (Manrope, defined in core:ui Type.kt).",
            )
        val RAW_DIMENSION =
            issue(
                "ObehyRawDimension",
                "Dimension outside the tokens",
                "Gaps, paddings and sizes come from `ObehyTheme.spacing` and `ObehyTheme.sizes` (core:ui Dimens.kt) " +
                    "or from a core:ui component. Need a new size? Add a token there.",
            )
        val RAW_SHAPE =
            issue(
                "ObehyRawShape",
                "Shape outside the theme",
                "Shapes come from `MaterialTheme.shapes`, `MaterialShapes` or the components' own defaults.",
            )
        val HARDCODED_TEXT =
            issue(
                "ObehyHardcodedText",
                "Text not from string resources",
                "Everything people read or hear comes from string resources, with an English (`values`) and a " +
                    "Czech (`values-cs`) version.",
            )

        val STATIC_BUTTON =
            issue(
                "ObehyStaticButton",
                "Button without shape morphing",
                "Material 3 Expressive buttons change shape when pressed, but only through the overloads that " +
                    "take `shapes`.",
            )

        val ISSUES = listOf(RAW_COLOR, RAW_TYPOGRAPHY, RAW_DIMENSION, RAW_SHAPE, HARDCODED_TEXT, STATIC_BUTTON)
    }
}
