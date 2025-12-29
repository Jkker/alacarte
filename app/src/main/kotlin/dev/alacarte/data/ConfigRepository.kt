package dev.alacarte.data

import android.content.Context
import com.highcapable.yukihookapi.hook.factory.prefs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ConfigRepository {
    private const val KEY_CONFIG = "menu_config"

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    // Default configuration
    val DEFAULT_CONFIG =
        MenuConfig(
            items =
                listOf(
                    MenuItemConfig("Copy", "复制"),
                    MenuItemConfig("Web search", "搜索"),
                    MenuItemConfig("Search in Edge", "搜索"),
                    MenuItemConfig("Select all", "全选"),
                    MenuItemConfig("Eudic", "词典", packageName = "com.eusoft.eudic"),
                    MenuItemConfig("Share", "分享"),
                    MenuItemConfig("Read aloud", "朗读"),
                    MenuItemConfig("Translate", "谷歌翻译", packageName = "com.google.android.apps.translate"),
                    MenuItemConfig("Translate", "系统翻译", packageName = "com.coloros.translate"),
                ),
            hiddenItems = listOf("AI Writer", "Define"),
            isDebug = true,
        )

    fun getConfig(prefs: com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge): MenuConfig {
        val jsonString = prefs.getString(KEY_CONFIG, "")
        return if (jsonString.isBlank()) {
            DEFAULT_CONFIG
        } else {
            try {
                json.decodeFromString<MenuConfig>(jsonString)
            } catch (e: Exception) {
                DEFAULT_CONFIG
            }
        }
    }

    fun saveConfig(
        context: Context,
        config: MenuConfig,
    ) {
        val jsonString = json.encodeToString(config)
        context.prefs().edit { putString(KEY_CONFIG, jsonString) }
    }

    // For usage in Hook (PackageParam has direct access to prefs)
    // Actually PackageParam.prefs is the same type as Context.prefs() wrapper in YukiHookAPI
}
