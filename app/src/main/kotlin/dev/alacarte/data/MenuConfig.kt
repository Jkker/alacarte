package dev.alacarte.data

import kotlinx.serialization.Serializable

@Serializable
data class MenuConfig(
    val items: List<MenuItemConfig> = emptyList(),
    val hiddenItems: List<String> = emptyList(),
    val isDebug: Boolean = false,
)

@Serializable
data class MenuItemConfig(
    val key: String,
    val customLabel: String,
    val isEnabled: Boolean = true,
    val packageName: String? = null,
)
