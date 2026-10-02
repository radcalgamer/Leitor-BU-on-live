package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SISTEMA,
    CLARO,
    ESCURO
}

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("leitor_bu_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getSavedThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _abrirResultadoAutomatico = MutableStateFlow(prefs.getBoolean(KEY_ABRIR_AUTO, true))
    val abrirResultadoAutomatico: StateFlow<Boolean> = _abrirResultadoAutomatico.asStateFlow()

    private val _lanternaAutomatica = MutableStateFlow(prefs.getBoolean(KEY_LANTERNA_AUTO, false))
    val lanternaAutomatica: StateFlow<Boolean> = _lanternaAutomatica.asStateFlow()

    private fun getSavedThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME, ThemeMode.SISTEMA.name)
        return try {
            ThemeMode.valueOf(name ?: ThemeMode.SISTEMA.name)
        } catch (e: Exception) {
            ThemeMode.SISTEMA
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setAbrirResultadoAutomatico(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ABRIR_AUTO, enabled).apply()
        _abrirResultadoAutomatico.value = enabled
    }

    fun setLanternaAutomatica(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LANTERNA_AUTO, enabled).apply()
        _lanternaAutomatica.value = enabled
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_ABRIR_AUTO = "abrir_resultado_auto"
        private const val KEY_LANTERNA_AUTO = "lanterna_auto"

        @Volatile
        private var INSTANCE: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = AppPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
