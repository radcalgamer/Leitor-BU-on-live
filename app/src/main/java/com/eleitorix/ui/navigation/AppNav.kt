package com.eleitorix.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eleitorix.data.preferences.ThemeMode
import com.eleitorix.ui.components.MainTopAppBar
import com.eleitorix.ui.screens.CandidatosScreen
import com.eleitorix.ui.screens.ConfiguracoesScreen
import com.eleitorix.ui.screens.HistoricoScreen
import com.eleitorix.ui.screens.ResultadosScreen
import com.eleitorix.ui.screens.ScannerScreen

enum class AppDestination(
    val route: String,
    val title: String,
    val iconFilled: ImageVector,
    val iconOutlined: ImageVector
) {
    RESULTADOS("resultados", "Resultados", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    SCANNER("scanner", "Scanner", Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner),
    HISTORICO("historico", "Histórico", Icons.Filled.History, Icons.Outlined.History),
    CANDIDATOS("candidatos", "Candidatos", Icons.Filled.Group, Icons.Outlined.Group),
    CONFIGURACOES("configuracoes", "Ajustes", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MainAppNavigation(
    currentThemeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(AppDestination.RESULTADOS) }

    Scaffold(
        topBar = {
            if (currentDestination != AppDestination.SCANNER) {
                MainTopAppBar(
                    title = currentDestination.title,
                    currentThemeMode = currentThemeMode,
                    onThemeModeChanged = onThemeModeChanged,
                    subtitulo = when (currentDestination) {
                        AppDestination.RESULTADOS -> "Apuração de Boletins de Urna"
                        AppDestination.HISTORICO -> "Urnas e Seções Auditadas"
                        AppDestination.CANDIDATOS -> "Gerenciador e Importador de CSV"
                        AppDestination.CONFIGURACOES -> "Tema e Preferências"
                        else -> null
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                AppDestination.values().forEach { destination ->
                    val isSelected = currentDestination == destination
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) destination.iconFilled else destination.iconOutlined,
                                contentDescription = destination.title
                            )
                        },
                        label = {
                            Text(
                                text = destination.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
                        fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                },
                label = "NavigationAnimation"
            ) { dest ->
                when (dest) {
                    AppDestination.RESULTADOS -> {
                        ResultadosScreen(
                            onNavigateToScanner = { currentDestination = AppDestination.SCANNER }
                        )
                    }
                    AppDestination.SCANNER -> {
                        ScannerScreen(
                            onBuSaved = {
                                currentDestination = AppDestination.RESULTADOS
                            }
                        )
                    }
                    AppDestination.HISTORICO -> {
                        HistoricoScreen(
                            onNavigateToScanner = { currentDestination = AppDestination.SCANNER }
                        )
                    }
                    AppDestination.CANDIDATOS -> {
                        CandidatosScreen()
                    }
                    AppDestination.CONFIGURACOES -> {
                        ConfiguracoesScreen(
                            currentThemeMode = currentThemeMode,
                            onThemeModeChanged = onThemeModeChanged
                        )
                    }
                }
            }
        }
    }
}
