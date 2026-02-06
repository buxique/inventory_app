package com.example.inventory

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.inventory.ui.InventoryApp
import com.example.inventory.ui.theme.InventoryAppTheme
import com.example.inventory.ui.viewmodel.AppViewModelFactory
import com.example.inventory.util.PrefsKeys
import com.example.inventory.util.settingsDataStore
import kotlinx.coroutines.flow.map
import java.util.Locale

/**
 * 主活动
 * 
 * 应用入口，使用简洁风格主题
 * 支持用户自定义语言设置，默认为中文
 * 支持夜间模式实时切换
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as InventoryApplication).container
        val factory = AppViewModelFactory(container, this)
        
        setContent {
            val context = LocalContext.current
            val darkMode by context.settingsDataStore.data
                .map { prefs -> prefs[PrefsKeys.DARK_MODE_PREF_KEY] ?: false }
                .collectAsState(initial = false)
            val fontScale by context.settingsDataStore.data
                .map { prefs -> prefs[PrefsKeys.FONT_SCALE_PREF_KEY] ?: 1.0f }
                .collectAsState(initial = 1.0f)
            
            // 使用应用主题，应用字号缩放和夜间模式
            InventoryAppTheme(
                darkTheme = darkMode,
                fontScale = fontScale
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InventoryApp(factory)
                }
            }
        }
    }
    
    override fun attachBaseContext(newBase: Context) {
        val languageCode = (application as? InventoryApplication)?.languageTag ?: "zh"
        
        val locale = when (languageCode) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            "system" -> Locale.getDefault() // 跟随系统语言
            else -> Locale.SIMPLIFIED_CHINESE // 默认中文
        }
        
        super.attachBaseContext(updateLocale(newBase, locale))
    }
    
    /**
     * 更新应用语言环境
     * 
     * @param context 上下文
     * @param locale 目标语言
     * @return 更新后的上下文
     */
    private fun updateLocale(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
