package cz.obehy.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.obehy.core.model.ThemeMode
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.feature.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app: AppViewModel = hiltViewModel()
            val theme by app.theme.collectAsStateWithLifecycle()
            val dark =
                when (theme) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            // The status and navigation bar icons follow the app, not the phone.
            DisposableEffect(dark) {
                val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            ObehyTheme(dark = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val state by app.state.collectAsStateWithLifecycle()
                    when (val s = state) {
                        AppState.Loading -> Box(Modifier.fillMaxSize())

                        // Onboarding finishes by saving the settings; the state then turns Ready by itself.
                        AppState.NeedsOnboarding -> OnboardingScreen()

                        is AppState.Ready -> ObehyShell(s.mode)
                    }
                }
            }
        }
    }
}
