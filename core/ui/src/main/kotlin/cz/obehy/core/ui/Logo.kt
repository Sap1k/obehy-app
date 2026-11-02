package cz.obehy.core.ui

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Oběhy wordmark in one colour: Petrol on light, Lime on Night (the theme's primary), as
 * the brand foundations ask.
 */
@Composable
fun ObehyLogo(modifier: Modifier = Modifier, height: Dp = 40.dp, color: Color = MaterialTheme.colorScheme.primary) {
    Icon(painterResource(R.drawable.obehy_logo), contentDescription = "Oběhy", modifier.height(height), tint = color)
}
