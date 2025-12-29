# AGENTS.md

## Context for Coding Agents

This repository contains an **LSPosed Module** targeting **Microsoft Edge (`com.microsoft.emmx`)**.
The goal is to intercept and modify the Android Text Selection Context Menu (`ActionMode`).

### Core Architecture

1.  **Module Entry (`HookEntry.kt`):**
    - **Framework:** uses `YukiHookAPI`.
    - **Hook Point:** `android.view.View.startActionMode(ActionMode.Callback, int)`.
    - **Mechanism:** Wraps the original `ActionMode.Callback` with a `CallbackProxy`.
    - **Logic:**
      - intercepts `onPrepareActionMode` (and `onCreateActionMode`).
      - Clears the menu.
      - Re-populates it based on `MenuConfig` loaded from prefs.
      - Applies sorting, renaming, hiding, and icon injection.

2.  **Configuration (`MenuConfig.kt` & `ConfigRepository.kt`):**
    - **Format:** JSON (Kotlinx Serialization).
    - **Storage:** Shared Preferences (`menu_config` string).
    - **IPC:** The Hook process reads prefs directly via Xposed bridge.

3.  **UI (`MainActivity.kt`):**
    - **Framework:** Jetpack Compose + Material 3.
    - **State:** Simple `mutableStateOf` driven UI.
    - **Logs:** Receives broadcasts (`dev.alacarte.LOG_BROADCAST`) from the hook process to display real-time debug info.

### Key Files

- `app/src/main/kotlin/dev/alacarte/hook/HookEntry.kt`: **The Brain.** All hooking logic is here.
- `app/src/main/kotlin/dev/alacarte/ui/MainActivity.kt`: **The Face.** Configuration UI and Log Console.
- `app/src/main/kotlin/dev/alacarte/data/MenuConfig.kt`: Data model for rules.

### Guidelines for Modification

1.  **Environment & Tools:**
    *   **Strictly use `mise`:** Always adhere to the tool versions defined in `mise.toml` to ensure environment consistency.
    *   **Use Mise Tasks:** Prefer running defined tasks (`mise run build`, `mise run check`) over raw gradle commands.
    *   **Documentation:** Use `context7` to lookup documentation for any libraries or tools you are working with.

2.  **Hooking Safety:**
    - **NEVER** hook internal Edge classes (obfuscated names like `j75`). They change every update.
    - Stick to Android SDK APIs (`View`, `ActionMode`, `Menu`, `PackageManager`) which are stable.
    - Always wrap hooks in `try-catch` blocks to prevent crashing the target app.

3.  **UI/UX:**
    - Maintain **Dark Mode** support.
    - Use **Material 3** components.
    - Keep the Log Console functional (it's critical for users to identify item keys).

3.  **Building & Verification:**
    - Use `mise run build` to build the debug APK.
    - Use `mise run release` to build the release APK.
    - Use `mise run check` to run all static analysis and formatting checks.
    - Use `mise run fmt` to auto-format code.
    - The `release` build needs a signing key (standard Android practice).

4.  **Debugging:**
    - Use the "Debug Logging" toggle in the app.
    - The hook sends ordered broadcasts.
    - Logs show `Before: ...` and `After: ...` snapshots of the menu.

### Common Issues

- **Duplicate Items:** Edge might add "Translate", and another app adds "Translate". Use `packageName` in `MenuItemConfig` to distinguish them.
- **Icon Missing:** Internal actions (Copy/Paste) often lack icons in the raw menu. The hook manually re-injects system drawables (`android.R.drawable.*`) for these.
