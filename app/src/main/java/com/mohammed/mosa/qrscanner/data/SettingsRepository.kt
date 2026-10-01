package com.mohammed.mosa.qrscanner.data


import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mohammed.mosa.qrscanner.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val autoSave: Boolean = true,
    val continuousScan: Boolean = false,
    val autoCopy: Boolean = false,
    val autoOpenUrl: Boolean = false,
    val sound: Boolean = true,
    val vibrate: Boolean = true,
    val useFrontCamera: Boolean = false,
    val accent: String = DEFAULT_ACCENT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    companion object { const val DEFAULT_ACCENT = "#7C5CFC" }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository private constructor(private val context: Context) {

    private object Keys {
        val autoSave = booleanPreferencesKey("auto_save")
        val continuousScan = booleanPreferencesKey("continuous_scan")
        val autoCopy = booleanPreferencesKey("auto_copy")
        val autoOpenUrl = booleanPreferencesKey("auto_open_url")
        val sound = booleanPreferencesKey("sound")
        val vibrate = booleanPreferencesKey("vibrate")
        val useFrontCamera = booleanPreferencesKey("front_camera")
        val accent = stringPreferencesKey("accent")
        val theme = stringPreferencesKey("theme")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            autoSave = p[Keys.autoSave] ?: true,
            continuousScan = p[Keys.continuousScan] ?: false,
            autoCopy = p[Keys.autoCopy] ?: false,
            autoOpenUrl = p[Keys.autoOpenUrl] ?: false,
            sound = p[Keys.sound] ?: true,
            vibrate = p[Keys.vibrate] ?: true,
            useFrontCamera = p[Keys.useFrontCamera] ?: false,
            accent = p[Keys.accent] ?: AppSettings.DEFAULT_ACCENT,
            themeMode = runCatching {
                ThemeMode.valueOf(p[Keys.theme] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
        )
    }

    suspend fun setAutoSave(v: Boolean) = context.dataStore.edit { it[Keys.autoSave] = v }
    suspend fun setContinuousScan(v: Boolean) = context.dataStore.edit { it[Keys.continuousScan] = v }
    suspend fun setAutoCopy(v: Boolean) = context.dataStore.edit { it[Keys.autoCopy] = v }
    suspend fun setAutoOpenUrl(v: Boolean) = context.dataStore.edit { it[Keys.autoOpenUrl] = v }
    suspend fun setSound(v: Boolean) = context.dataStore.edit { it[Keys.sound] = v }
    suspend fun setVibrate(v: Boolean) = context.dataStore.edit { it[Keys.vibrate] = v }
    suspend fun setUseFrontCamera(v: Boolean) = context.dataStore.edit { it[Keys.useFrontCamera] = v }
    suspend fun setAccent(hex: String) = context.dataStore.edit { it[Keys.accent] = hex }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }

    companion object {
        @Volatile private var instance: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}