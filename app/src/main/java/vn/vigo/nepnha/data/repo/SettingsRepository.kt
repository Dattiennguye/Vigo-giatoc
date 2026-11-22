package vn.vigo.nepnha.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.vigo.nepnha.kinship.Dialect

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val fontScale: Float = 1f,
    val dialect: Dialect = Dialect.NORTH,
    val defaultHour: Int = 8,
    val reduceMotion: Boolean = false,
    val showHolidays: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private object K {
        val fontScale = floatPreferencesKey("font_scale")
        val dialect = stringPreferencesKey("dialect")
        val defaultHour = intPreferencesKey("default_hour")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val showHolidays = booleanPreferencesKey("show_holidays")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            fontScale = p[K.fontScale] ?: 1f,
            dialect = runCatching { Dialect.valueOf(p[K.dialect] ?: "NORTH") }.getOrDefault(Dialect.NORTH),
            defaultHour = p[K.defaultHour] ?: 8,
            reduceMotion = p[K.reduceMotion] ?: false,
            showHolidays = p[K.showHolidays] ?: true,
        )
    }

    suspend fun setFontScale(v: Float) = context.dataStore.edit { it[K.fontScale] = v.coerceIn(1f, 1.5f) }
    suspend fun setDialect(v: Dialect) = context.dataStore.edit { it[K.dialect] = v.name }
    suspend fun setDefaultHour(v: Int) = context.dataStore.edit { it[K.defaultHour] = v.coerceIn(0, 23) }
    suspend fun setReduceMotion(v: Boolean) = context.dataStore.edit { it[K.reduceMotion] = v }
    suspend fun setShowHolidays(v: Boolean) = context.dataStore.edit { it[K.showHolidays] = v }
}
