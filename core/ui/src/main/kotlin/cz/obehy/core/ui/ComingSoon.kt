package cz.obehy.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign

/** A placeholder for a screen that is not built yet. */
@Composable
fun ComingSoon(
    title: String,
    modifier: Modifier = Modifier,
) {
    ObehyScreen(
        title = title,
        modifier = modifier,
        titleBar = TitleBar.Small,
        scrollable = false,
        verticalArrangement = Arrangement.spacedBy(ObehyTheme.spacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.obehy_symbol),
            contentDescription = null,
            modifier = Modifier.size(ObehyTheme.sizes.illustration),
            tint = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            stringResource(R.string.coming_soon),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
