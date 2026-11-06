package cz.obehy.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import cz.obehy.core.ui.ObehyTheme

/**
 * What stands where content would be: nothing found, or something that didn't work. The [icon] says
 * what kind of thing it is, [text] says it in words, and [action] is the way out, if there is one.
 */
@Composable
fun StateMessage(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = ObehyTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(ObehyTheme.sizes.illustration)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(
            text,
            Modifier.widthIn(max = ObehyTheme.sizes.paragraphWidth),
            style = MaterialTheme.typography.titleMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        action?.let {
            FilledTonalButton(onClick = onAction, shapes = ButtonDefaults.shapes()) { Text(it) }
        }
    }
}
