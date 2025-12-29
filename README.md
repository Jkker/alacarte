# à la carte

**à la carte** is an Android **LSPosed (Xposed)** module designed to take full control of the text selection context menu in **Microsoft Edge for Android** (and potentially other apps).

It solves the problem of cluttered, disordered, and inconsistent context menus by allowing users to:

- **Reorder** menu items (e.g., move "Web Search" to the top).
- **Rename** items (e.g., "Web search" -> "Search").
- **Hide** unwanted items (e.g., "AI Writer", "Define", "Bing Search").
- **Unify** icons for 3rd-party apps and internal actions.

## Features

- **🪝 Robust Hooking:** Uses `View.startActionMode` interception to reliably modify the menu _after_ the app populates it but _before_ it shows, ensuring compatibility with Edge's dynamic menu generation.
- **🎨 Material 3 UI:** A modern, Jetpack Compose-based configuration app.
- **⚙️ Dynamic Config:** Changes are applied instantly (or on next menu open) via standard Android Preferences.
- **📦 Package Awareness:** Distinguish between items with the same title (e.g., "Translate" from Google vs. System) by inspecting their package source.
- **🖼️ Smart Icons:** Automatically loads app icons for 3rd-party menu items and injects system icons for standard actions like Copy/Share if missing.
- **🐞 Live Debug Console:** Built-in terminal to view real-time menu logs, helping you identify package names and debug rules.

## Requirements

- **Rooted Android Device**
- **LSPosed** (Zygisk or Riru) installed and active.
- **Microsoft Edge for Android** (Target package: `com.microsoft.emmx`).

## Installation

1.  Download and install the `app-release.apk`.
2.  Open the **LSPosed Manager** notification.
3.  Enable **à la carte** module.
4.  Check **Microsoft Edge** in the scope list.
5.  **Force Stop** Microsoft Edge to apply hooks.
6.  Open the **à la carte** app to configure your preferences.

## Usage

### Configuration

1.  **Reorder:** Use the Up/Down arrows to change the position of pinned items.
2.  **Rename:** Edit the text field to change the label.
3.  **Toggle:** Use the switch to Enable/Disable items.
4.  **Hide:** Add items to the "Hidden Items" list to remove them completely.
5.  **Add New:** Click the FAB (+) to add new rules. You can specify:
    - **Key:** The original title of the item (e.g., "Translate").
    - **Package Name (Optional):** To target a specific app (e.g., `com.google.android.apps.translate`).
    - **Custom Label:** Your desired name.

### Debugging

If an item isn't behaving as expected:

1.  Go to the app's **Config** tab.
2.  Enable **"Debug Logging"**.
3.  Switch to the **Logs** tab.
4.  Open Edge and select text.
5.  View the logs to see the exact "Title" and "[Package Name]" of the items appearing.
6.  Use this info to create precise rules.

## Development Setup

### Environment Management with Mise
This project uses **mise** to manage development tools and environment variables consistently.
1.  Install [mise](https://mise.jdx.dev/).
2.  Run `mise install` in the project root to set up the environment defined in `mise.toml` (Android SDK, Gradle, Java, Kotlin, etc.).

### Development Commands
*   `mise run build`: Build the debug APK.
*   `mise run release`: Build the release APK (unsigned).
*   `mise run check`: Run linting and static analysis.
*   `mise run fmt`: Auto-format code.
*   `mise run clean`: Clean build artifacts.

### Documentation Lookup

Use `context7` to lookup documentation for libraries and tools used in this project.

## Technical Details & Privacy

This module performs specific modifications to the target application's memory structure at runtime. Here is a transparent breakdown of what it does:

### 1. Menu Interception
*   **Mechanism:** Hooks `android.view.View.startActionMode`.
*   **Action:** It creates a "Proxy" callback that sits between the App and the System. When the menu is about to be shown (`onPrepareActionMode`), this module takes control to reorganize it.

### 2. System Injection Blocking
*   **Mechanism:** Sets `TextClassifier` to `NO_OP` on the WebView.
*   **Reason:** Android's "Smart Text Selection" and OEM layers often inject unwanted buttons (like "AI Writer", "Translate", or "Search") that cannot be removed by normal means.
*   **Effect:** This forces the system to stop interfering, leaving only the legitimate menu items for you to manage.

### 3. Icon Injection
*   **Mechanism:** Uses `PackageManager` to retrieve the icon of the app associated with a menu item.
*   **Reason:** Many browser menus only show text. This restores visual cues by adding the target app's icon (e.g., the Google Translate icon next to the "Translate" button).

### 4. Privacy Policy
*   **No Internet Access:** This module does not request the `INTERNET` permission.
*   **Local Processing:** All logic (sorting, renaming, logging) happens locally on your device.
*   **No Data Collection:** The "Logs" tab in the app displays data *broadcasted* from the hook for your own debugging. This data is not saved to disk or sent anywhere.

## Architecture

- **Language:** Kotlin
- **Hooking Framework:** [YukiHookAPI](https://github.com/HighCapable/YukiHookAPI) (Modern wrapper for Xposed).
- **UI Framework:** Jetpack Compose (Material 3).
- **Data Persistence:** Kotlinx Serialization (JSON) + XSharedPreferences.

## License

[MIT License](LICENSE)
