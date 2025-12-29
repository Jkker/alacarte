package dev.alacarte.hook

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.textclassifier.TextClassifier
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClass
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import dev.alacarte.data.ConfigRepository

/**
 * LSPosed Module Entry Point
 * Customizes Microsoft Edge's text selection context menu
 */
@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {
    private const val TAG = "alacarte"
    private const val TARGET_PACKAGE = "com.microsoft.emmx"

    // Track nesting to avoid infinite recursion
    private val isCustomizing = ThreadLocal<Boolean>()

    override fun onInit() =
        configs {
            isDebug = true
        }

    override fun onHook() =
        encase {
            loadApp(name = TARGET_PACKAGE) {
                log(null, "alacarte Hook Loaded")

                // Capture prefs here to pass down
                val hookPrefs = prefs

                View::class.java.name.toClass().method {
                    name = "startActionMode"
                    param(ActionMode.Callback::class.java, IntType)
                    returnType = ActionMode::class.java
                }.hook {
                    before {
                        val originalCallback = args[0] as? ActionMode.Callback ?: return@before
                        val type = args[1] as? Int ?: 0

                        val view = instance as? View
                        val context = view?.context

                        // Disable TextClassifier
                        //
                        // [TRANSPARENCY NOTE]:
                        // The TextClassifier is an Android system component responsible for "smart" actions
                        // (e.g., highlighting an address and suggesting Maps).
                        // In Edge and other browsers, this component is often used by the system or OEM layers
                        // to inject unwanted menu items like "AI Writer", "Translate", or "Search" that clutter the menu.
                        //
                        // We set it to NO_OP (No Operation) to:
                        // 1. Prevent these unconfigurable system injections.
                        // 2. Ensure the menu only contains items from the App or standard Android Intents.
                        // 3. Give the user full control over the menu structure without fighting the OS.
                        if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                val method = View::class.java.getMethod("setTextClassifier", TextClassifier::class.java)
                                method.invoke(view, TextClassifier.NO_OP)
                            } catch (e: Throwable) {
                                // Suppress errors:
                                // This is a "best effort" cleanup. On some devices/ROMs, this might fail or the View
                                // might not support it. We don't want to crash the host app or spam logs for this.
                            }
                        }

                        if (type == ActionMode.TYPE_FLOATING) {
                            log(context, "Intercepting startActionMode", hookPrefs)
                            args[0] = CallbackProxy(originalCallback, context, hookPrefs)
                        }
                    }
                }
            }
        }

    private fun log(
        context: Context?,
        msg: String,
        prefs: YukiHookPrefsBridge? = null,
    ) {
        var isDebug = true

        if (prefs != null) {
            val config = ConfigRepository.getConfig(prefs)
            isDebug = config.isDebug
        }

        if (!isDebug) return

        YLog.info(tag = TAG, msg = msg)

        try {
            context?.sendBroadcast(
                Intent("dev.alacarte.LOG_BROADCAST").apply {
                    setPackage("dev.alacarte")
                    putExtra("msg", msg)
                },
            )
        } catch (e: Exception) {
            YLog.error(tag = TAG, msg = "Failed to send broadcast", e = e)
        }
    }

    private class CallbackProxy(
        private val original: ActionMode.Callback,
        private val context: Context?,
        private val prefs: YukiHookPrefsBridge,
    ) : ActionMode.Callback2() {
        override fun onCreateActionMode(
            mode: ActionMode,
            menu: Menu,
        ): Boolean {
            val result = original.onCreateActionMode(mode, menu)
            HookEntry.customizeMenu(menu, context, prefs)
            return result
        }

        override fun onPrepareActionMode(
            mode: ActionMode,
            menu: Menu,
        ): Boolean {
            val result = original.onPrepareActionMode(mode, menu)
            HookEntry.customizeMenu(menu, context, prefs)
            return result
        }

        override fun onActionItemClicked(
            mode: ActionMode,
            item: MenuItem,
        ): Boolean {
            HookEntry.log(context, "Clicked: '${item.title}'", prefs)
            return original.onActionItemClicked(mode, item)
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            original.onDestroyActionMode(mode)
        }

        override fun onGetContentRect(
            mode: ActionMode,
            view: View,
            outRect: Rect,
        ) {
            if (original is ActionMode.Callback2) {
                original.onGetContentRect(mode, view, outRect)
            } else {
                super.onGetContentRect(mode, view, outRect)
            }
        }
    }

    fun customizeMenu(
        menu: Menu,
        context: Context?,
        prefs: YukiHookPrefsBridge,
    ) {
        // [MODIFICATION SUMMARY]
        // This is the core logic where the user's configuration is applied to the Android Menu.
        // Actions performed:
        // 1. READ: Inspects all current menu items (Title, Intent, PackageName).
        // 2. FILTER: Removes items listed in the 'Hidden Items' config.
        // 3. SORT: Reorders items based on the user's preferred order.
        // 4. RENAME: Changes item titles to user's custom labels.
        // 5. ENHANCE: Injects icons for items that lack them (using App Icons or System Drawables).
        //
        // No data is collected or transmitted. All processing happens locally in memory.

        if (isCustomizing.get() == true) return
        isCustomizing.set(true)

        try {
            if (menu.size() == 0) return

            val config = ConfigRepository.getConfig(prefs)

            // Collect items
            val items = mutableListOf<MenuItemData>()
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                items.add(MenuItemData.fromMenuItem(item))
            }

            // Log before state with rich info
            val beforeLog =
                items.joinToString("\n") {
                    val pkg = it.intent?.component?.packageName ?: "internal"
                    "- ${it.title} [$pkg]"
                }
            log(context, "Before:\n$beforeLog", prefs)

            // Sort
            val sortedItems =
                items.sortedWith { a, b ->
                    val aConfig = findConfig(a, config)
                    val bConfig = findConfig(b, config)

                    val aIndex = if (aConfig != null) config.items.indexOf(aConfig) else -1
                    val bIndex = if (bConfig != null) config.items.indexOf(bConfig) else -1

                    when {
                        aIndex >= 0 && bIndex >= 0 -> aIndex - bIndex
                        aIndex >= 0 -> -1
                        bIndex >= 0 -> 1
                        else -> a.originalOrder - b.originalOrder
                    }
                }

            menu.clear()

            sortedItems.forEachIndexed { index, itemData ->
                // Hide if in hidden list OR if explicitly disabled in items list
                if (itemData.title in config.hiddenItems) return@forEachIndexed

                val configItem = findConfig(itemData, config)
                if (configItem != null && !configItem.isEnabled) return@forEachIndexed

                // Rename
                val newTitle = configItem?.customLabel ?: itemData.title

                val newItem = menu.add(itemData.groupId, itemData.itemId, index, newTitle)

                // Icon Logic:
                // 1. If original item had an icon, use it.
                // 2. If it's a 3rd party app (has packageName), try to load app icon.
                // 3. If it's a known internal action, try to use system drawable.
                var icon = itemData.icon

                if (icon == null && itemData.packageName != null && context != null) {
                    try {
                        val pm = context.packageManager
                        icon = pm.getApplicationIcon(itemData.packageName)
                    } catch (e: Exception) {
                        // Failed to load app icon
                    }
                }

                // Fallback for known internal actions if no icon exists
                if (icon == null) {
                    icon =
                        when (itemData.title) {
                            "Copy", "复制" ->
                                context?.getDrawable(android.R.drawable.ic_menu_save) // Fallback
                            "Share", "分享" ->
                                context?.getDrawable(android.R.drawable.ic_menu_share)
                            "Select all", "全选" ->
                                context?.getDrawable(android.R.drawable.ic_menu_agenda)
                            "Web search", "搜索" ->
                                context?.getDrawable(android.R.drawable.ic_menu_search)
                            else -> null
                        }
                }

                if (icon != null) {
                    newItem.icon = icon
                }

                itemData.intent?.let { newItem.intent = it }
                newItem.isVisible = itemData.isVisible
                newItem.isEnabled = itemData.isEnabled
                newItem.isCheckable = itemData.isCheckable
                newItem.isChecked = itemData.isChecked

                try {
                    newItem.setShowAsAction(itemData.showAsAction)
                } catch (e: Exception) {
                }
            }

            // Log after state
            val finalItems = mutableListOf<String>()
            for (i in 0 until menu.size()) {
                finalItems.add(menu.getItem(i).title.toString())
            }
            log(context, "After: [${finalItems.joinToString(", ")}]", prefs)
        } catch (e: Exception) {
            log(context, "Error: ${e.message}", prefs)
        } finally {
            isCustomizing.set(false)
        }
    }

    private fun findConfig(
        item: MenuItemData,
        config: dev.alacarte.data.MenuConfig,
    ): dev.alacarte.data.MenuItemConfig? {
        // First try to match by Title AND PackageName
        if (item.packageName != null) {
            val exactMatch = config.items.find { it.key == item.title && it.packageName == item.packageName }
            if (exactMatch != null) return exactMatch
        }

        // Fallback: Match by Title only (if config has no package name specified)
        return config.items.find { it.key == item.title && it.packageName == null }
    }

    private data class MenuItemData(
        val groupId: Int,
        val itemId: Int,
        val originalOrder: Int,
        val title: String,
        val icon: Drawable?,
        val intent: Intent?,
        val packageName: String?,
        val isVisible: Boolean,
        val isEnabled: Boolean,
        val isCheckable: Boolean,
        val isChecked: Boolean,
        val showAsAction: Int,
    ) {
        companion object {
            fun fromMenuItem(item: MenuItem): MenuItemData {
                return MenuItemData(
                    groupId = item.groupId,
                    itemId = item.itemId,
                    originalOrder = item.order,
                    title = item.title?.toString() ?: "",
                    icon = runCatching { item.icon }.getOrNull(),
                    intent = runCatching { item.intent }.getOrNull(),
                    packageName = runCatching { item.intent?.component?.packageName }.getOrNull(),
                    isVisible = item.isVisible,
                    isEnabled = item.isEnabled,
                    isCheckable = item.isCheckable,
                    isChecked = item.isChecked,
                    showAsAction =
                        try {
                            val method = item.javaClass.getMethod("getShowAsAction")
                            method.invoke(item) as? Int ?: MenuItem.SHOW_AS_ACTION_IF_ROOM
                        } catch (e: Exception) {
                            MenuItem.SHOW_AS_ACTION_IF_ROOM
                        },
                )
            }
        }
    }
}
