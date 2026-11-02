package cz.obehy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.obehy.core.ui.ObehyTheme
import cz.obehy.feature.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ObehyTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val app: AppViewModel = hiltViewModel()
                    val state by app.state.collectAsStateWithLifecycle()
                    when (val s = state) {
                        AppState.Loading -> Box(Modifier.fillMaxSize())
                        // Onboarding writes the settings; the state then turns Ready by itself.
                        AppState.NeedsOnboarding -> OnboardingScreen(onDone = {})
                        is AppState.Ready -> ObehyShell(s.mode)
                    }
                }
            }
        }
    }
}
