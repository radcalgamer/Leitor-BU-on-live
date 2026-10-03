package com.eleitorix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.eleitorix.data.preferences.AppPreferences
import com.eleitorix.data.repository.CandidatoRepository
import com.eleitorix.ui.navigation.MainAppNavigation
import com.eleitorix.ui.theme.LeitorBuTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val candidatoRepository = CandidatoRepository(this)
        lifecycleScope.launch {
            candidatoRepository.inicializarCandidatosEmbutidosSeNecessario()
        }

        val appPreferences = AppPreferences.getInstance(this)

        setContent {
            val themeMode by appPreferences.themeMode.collectAsState()

            LeitorBuTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppNavigation(
                        currentThemeMode = themeMode,
                        onThemeModeChanged = { newMode ->
                            appPreferences.setThemeMode(newMode)
                        }
                    )
                }
            }
        }
    }
}
