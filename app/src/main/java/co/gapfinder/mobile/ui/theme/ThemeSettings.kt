package co.gapfinder.mobile.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lo que eligió el estudiante en el selector de tema. */
enum class ThemeChoice { AUTO, LIGHT, DARK }

/**
 * Elección manual del tema, guardada en SharedPreferences para que se mantenga al reabrir la app.
 * AUTO (por defecto) usa el sensor de luz; LIGHT y DARK lo fijan a mano.
 */
object ThemeSettings {
    private const val FILE = "theme_settings"
    private const val KEY = "choice"

    private var prefs: SharedPreferences? = null
    private val state = MutableStateFlow(ThemeChoice.AUTO)
    val choice: StateFlow<ThemeChoice> = state.asStateFlow()

    fun attach(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).also { p ->
            state.value = p.getString(KEY, null)
                ?.let { saved -> ThemeChoice.entries.firstOrNull { it.name == saved } }
                ?: ThemeChoice.AUTO
        }
    }

    fun choose(choice: ThemeChoice) {
        state.value = choice
        prefs?.edit()?.putString(KEY, choice.name)?.apply()
    }
}
