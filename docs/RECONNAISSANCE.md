# Text Selection Context Menu Customization - Reconnaissance Report

## Project: SelectionMenuCustomizer (LSPosed Module)

**Date:** 2025-12-28  
**Target:** Android 16 (API 35) with KernelSU + LSPosed  
**Initial Target App:** Microsoft Edge (`com.microsoft.emmx`)

---

## Executive Summary

Successfully probed Android's text selection floating action mode (context menu) system. Identified hook points, menu item structure, and callback chain. Ready to build a configurable module for customizing menu items (reorder, rename, hide) across any app.

---

## 1. Android ActionMode Architecture

### 1.1 Text Selection Flow

```
User long-press on text
        ↓
View.startActionMode(Callback, TYPE_FLOATING)
        ↓
DecorView wraps callback in ActionModeCallback2Wrapper
        ↓
FloatingActionMode created
        ↓
Callback.onCreateActionMode(ActionMode, Menu) → populates menu
        ↓
Callback.onPrepareActionMode(ActionMode, Menu) → finalizes menu
        ↓
Activity.onActionModeStarted(ActionMode) → system notification
        ↓
Floating toolbar rendered with menu items
```

### 1.2 Key Classes

| Class | Role |
|-------|------|
| `android.view.ActionMode` | Abstract base for action modes |
| `android.view.ActionMode.Callback` | Interface for menu lifecycle |
| `android.view.ActionMode.Callback2` | Extends Callback with content rect |
| `com.android.internal.view.FloatingActionMode` | System floating toolbar implementation |
| `com.android.internal.policy.DecorView$ActionModeCallback2Wrapper` | System wrapper for callbacks |
| `com.android.internal.view.menu.MenuItemImpl` | Concrete MenuItem implementation |

### 1.3 ActionMode Types

| Type | Value | Description |
|------|-------|-------------|
| `TYPE_PRIMARY` | 0 | Traditional action bar mode |
| `TYPE_FLOATING` | 1 | Floating toolbar (text selection) |

---

## 2. Hook Points Analysis

### 2.1 Primary Hook Points

#### `Activity.onActionModeStarted(ActionMode mode)`
- **When:** After ActionMode is fully created and visible
- **Access:** Complete Menu with all items populated
- **Use Case:** Read-only inspection, late modification
- **Reliability:** ★★★★★ (always called)

#### `View.startActionMode(ActionMode.Callback callback, int type)`
- **When:** Before ActionMode creation
- **Access:** Original Callback object
- **Use Case:** Wrap/replace callback for full control
- **Reliability:** ★★★★★ (entry point)

#### `ActionMode.Callback.onPrepareActionMode(ActionMode, Menu)`
- **When:** Before menu is displayed, after creation
- **Access:** Mutable Menu object
- **Use Case:** Modify menu items (add/remove/reorder)
- **Reliability:** ★★★★☆ (may be called multiple times)

#### `ActionMode.Callback.onCreateActionMode(ActionMode, Menu)`
- **When:** During initial menu creation
- **Access:** Empty or partially filled Menu
- **Use Case:** Early interception
- **Reliability:** ★★★★☆ (called once per ActionMode)

### 2.2 Chromium-Specific (Edge)

| Class | Method | Purpose |
|-------|--------|---------|
| `org.chromium.content.browser.selection.SelectionPopupControllerImpl` | Various | Chromium's selection controller |
| `j75` (obfuscated) | Callback methods | Edge's ActionMode.Callback implementation |
| `di` (obfuscated) | Superclass | Base callback class |
| `wns` (obfuscated) | Delegate | Inner callback delegate |

---

## 3. Menu Item Structure

### 3.1 MenuItem Properties

```kotlin
interface MenuItem {
    val itemId: Int          // Unique identifier (0 for external apps)
    val groupId: Int         // Menu group
    val order: Int           // Display order (lower = earlier)
    val title: CharSequence  // Display text
    val titleCondensed: CharSequence?  // Short title
    val icon: Drawable?      // Optional icon
    val intent: Intent?      // For PROCESS_TEXT items
    var isVisible: Boolean   // Visibility flag
    var isEnabled: Boolean   // Enabled state
}
```

### 3.2 Item ID Patterns

| Pattern | Source | Example |
|---------|--------|---------|
| `0x7f01XXXX` | App resources (Edge/Chromium) | Copy: `0x7f011124` |
| `0x102XXXX` | Android system | Cut: `0x1020020` |
| `0x0` | External apps (PROCESS_TEXT) | AI Writer, Translate |
| `1000+` | App-defined constants | Read aloud: `0x3e8` |

### 3.3 Captured Menu Items (Edge Browser)

| Index | Title | ItemId | Order | Visible | Type |
|-------|-------|--------|-------|---------|------|
| 0 | Copy | `0x7f011124` | 11 | ✓ | Chromium |
| 1 | Share | `0x7f01112d` | 15 | ✓ | Chromium |
| 2 | Select all | `0x7f01112c` | 16 | ✓ | Chromium |
| 3 | Read aloud | `0x3e8` | 17 | ✓ | Edge |
| 4 | Web search | `0x7f011130` | 18 | ✓ | Chromium |
| 5 | AI Writer | `0x0` | 31 | ✓ | External |
| 6 | DIDA | `0x0` | 32 | ✓ | External |
| 7 | Translate | `0x0` | 33 | ✓ | External |
| 8 | Eudic | `0x0` | 34 | ✓ | External |
| 9 | Ask Meta AI | `0x0` | 35 | ✓ | External |
| 10 | Translate | `0x0` | 36 | ✓ | External |
| 11 | Search in Edge | `0x0` | 37 | ✗ | Edge |
| 12 | Ask M365 Copilot | `0x0` | 38 | ✓ | Edge |
| 13 | Quick note | `0x0` | 39 | ✓ | Edge |
| 14 | YJBrowser | `0x0` | 40 | ✗ | External |
| 15 | Speak | `0x0` | 41 | ✗ | External |

---

## 4. Implementation Strategy

### 4.1 Recommended Hook Approach

**Primary Strategy:** Hook `Activity.onActionModeStarted` to modify menu after all items are added.

```kotlin
// Pseudo-code
Activity.onActionModeStarted.hook {
    after {
        val actionMode = args[0] as ActionMode
        val menu = actionMode.menu
        
        // Apply user configuration
        applyMenuCustomization(packageName, menu)
    }
}
```

**Why this approach:**
1. All menu items (including PROCESS_TEXT) are already populated
2. Single hook point works for all apps
3. Menu object is fully mutable at this point
4. No need to wrap callbacks or handle obfuscated classes

### 4.2 Menu Modification Techniques

#### Hide Items
```kotlin
menuItem.isVisible = false
```

#### Reorder Items
```kotlin
// Menu items are ordered by `order` property
// To reorder, we need to:
// 1. Remove all items
// 2. Re-add in desired order with new order values
menu.clear()
sortedItems.forEachIndexed { index, item ->
    menu.add(item.groupId, item.itemId, index, item.title)
}
```

#### Rename Items
```kotlin
menuItem.setTitle("New Title")
```

### 4.3 Item Identification

Since external apps use `itemId = 0`, we need composite keys:

```kotlin
data class MenuItemKey(
    val title: String,      // Primary identifier
    val itemId: Int,        // Secondary (0 for external)
    val packageName: String // App context
)
```

---

## 5. Configuration Schema

### 5.1 Per-App Configuration

```kotlin
data class AppMenuConfig(
    val packageName: String,
    val enabled: Boolean = true,
    val items: List<MenuItemConfig> = emptyList()
)

data class MenuItemConfig(
    val originalTitle: String,  // Match key
    val itemId: Int = 0,        // Optional match key
    val customTitle: String? = null,  // Rename (null = keep original)
    val visible: Boolean = true,      // Hide if false
    val order: Int = -1               // Custom order (-1 = auto)
)
```

### 5.2 Storage

- Use `SharedPreferences` with `MODE_WORLD_READABLE` for Xposed access
- Or use LSPosed's modern `RemotePreferences` API
- JSON serialization for complex config

---

## 6. UI Requirements

### 6.1 Main Screen
- List of scoped apps with enable/disable toggle
- Add app button (package picker)

### 6.2 App Configuration Screen
- Header: App icon + name
- "Capture Menu" button to trigger learning mode
- List of captured menu items:
  - Drag handle for reorder
  - Title (editable on tap)
  - Visibility toggle
  - Reset button

### 6.3 Technical Stack
- Kotlin
- Jetpack Compose
- Material 3
- DataStore for preferences
- Room for captured items (optional)

---

## 7. Project Structure

```
SelectionMenuCustomizer/
├── app/
│   ├── src/main/
│   │   ├── java/dev/customizer/
│   │   │   ├── HookEntry.kt           # Xposed entry point
│   │   │   ├── MenuCustomizer.kt      # Core hook logic
│   │   │   ├── ConfigManager.kt       # Preference handling
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── theme/
│   │   │   │   ├── screens/
│   │   │   │   │   ├── AppListScreen.kt
│   │   │   │   │   ├── AppConfigScreen.kt
│   │   │   │   │   └── MenuItemEditor.kt
│   │   │   │   └── components/
│   │   │   │       ├── DraggableList.kt
│   │   │   │       └── MenuItemRow.kt
│   │   │   └── data/
│   │   │       ├── MenuItemConfig.kt
│   │   │       └── AppConfig.kt
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── settings.gradle.kts
└── build.gradle.kts
```

---

## 8. Development Phases

### Phase 1: Core Hook (v0.1) ✓ Complete
- [x] Project setup with YukiHookAPI
- [x] Hook Activity.onActionModeStarted
- [x] Log menu items
- [x] Test on Edge

---

## 9. Known Limitations

1. **External app items (PROCESS_TEXT)** have `itemId = 0`, requiring title-based matching
2. **Obfuscated classes** in apps like Edge change between versions
3. **Menu invalidation** - some apps may re-prepare menu, overwriting changes
4. **System apps** may have additional restrictions

---

## 10. References

- [Android ActionMode Documentation](https://developer.android.com/reference/android/view/ActionMode)
- [YukiHookAPI Documentation](https://highcapable.github.io/YukiHookAPI/en/)
- [LSPosed Wiki](https://github.com/LSPosed/LSPosed/wiki)
- [PROCESS_TEXT Intent](https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT)

---

## Appendix A: Raw Probe Logs

```
12-28 19:49:18.072 I SelectionProbe: ╔════════════════════════════════════════════════════════════
12-28 19:49:18.072 I SelectionProbe: ║ ACTIVITY.onActionModeStarted TRIGGERED
12-28 19:49:18.072 I SelectionProbe: ╠════════════════════════════════════════════════════════════
12-28 19:49:18.072 I SelectionProbe: ║ Activity: org.chromium.chrome.browser.ChromeTabbedActivity
12-28 19:49:18.072 I SelectionProbe: ║ ActionMode Type: FLOATING (1)
12-28 19:49:18.072 I SelectionProbe: ║ ActionMode Class: com.android.internal.view.FloatingActionMode
12-28 19:49:18.072 I SelectionProbe: ║ Title: null
12-28 19:49:18.072 I SelectionProbe: ╚════════════════════════════════════════════════════════════

12-28 19:49:17.980 I SelectionProbe: ║ VIEW.startActionMode CALLED
12-28 19:49:17.980 I SelectionProbe: ║ Callback Class: j75
12-28 19:49:17.980 I SelectionProbe: ║ Callback Superclass: di
12-28 19:49:17.980 I SelectionProbe: ║ Callback Interfaces: (none)

12-28 19:49:18.150 I SelectionProbe: ║ ActionMode Callback Details
12-28 19:49:18.150 I SelectionProbe: ║ Callback Class: DecorView$ActionModeCallback2Wrapper
12-28 19:49:18.150 I SelectionProbe: ║ Declared Fields: [mWrapped, this$0]
```

---

*Document generated from probe module: SelectionMenuProbe v1.0.0*
