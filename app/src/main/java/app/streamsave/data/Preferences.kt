package app.streamsave.data

import android.content.Context
import app.streamsave.parser.ResolverProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(val theme: Int = 0, val clipboard: Boolean = true, val resolver: ResolverProvider = ResolverProvider.WEBSITE)

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    init {
        // Version 1.1 does not consume user credentials. Discard the old encrypted value on upgrade.
        if (prefs.contains("cookie")) prefs.edit().remove("cookie").apply()
    }
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private fun read() = Settings(prefs.getInt("theme", 0), prefs.getBoolean("clipboard", true),
        ResolverProvider.entries.firstOrNull { it.name == prefs.getString("resolver", null) } ?: ResolverProvider.WEBSITE)
    fun resolver(value: ResolverProvider) { prefs.edit().putString("resolver", value.name).apply(); mutable.value = read() }
    fun theme(value: Int) { prefs.edit().putInt("theme", value.coerceIn(0, 2)).apply(); mutable.value = read() }
    fun clipboard(value: Boolean) { prefs.edit().putBoolean("clipboard", value).apply(); mutable.value = read() }
}
