# dumb-launcher

A keypad-first Android launcher for flip phones, built for the **ALT MIVE Style Folder 2 (AT-M140)**.
Everything works from the keys; touch is only a fallback.

## Screens

| Screen | Keys |
|---|---|
| **Home**: clock, missed calls, new messages | ● OK → Menu · 0-9 → dialer · ▲▼◀▶ → your 4 shortcut apps · 📞 → call log · ☆ → Options · 👤 ✉ 📷 → Contacts, Messages, Camera (any screen) |
| **Menu**: 3×3 grid | 1-9 open that cell at once · ● open · ☆ → change/empty a slot |
| **All apps**: A–Z list | type on the keypad to filter (T9: 2-2-6 → Camera) · 지움 deletes a digit · ↩ clears the search |
| **Key tester** | shows the code of every key; press ↩ twice to exit |

The bottom bar of every screen says what the keys do. `*` is left to the phone's own software.

Home → ☆ Options also has **Highlight colour** (10 colours) and **Icon style**
("Clean" removes the white box many system icons have; "Original" shows them unchanged).

## Install

1. Download `app-debug.apk` (from the **Build APK** workflow artifacts on GitHub, or a local build).
2. Install it on the phone (allow "install unknown apps").
3. Press Home and choose **Dumb Launcher**, or go to Options → *Choose default launcher*.
4. Allow call log and SMS access so Home can show missed calls and new messages.

## Key codes (measured on AT-M140)

| Key | Code | Use |
|---|---|---|
| ☆ | F3 (133), sometimes 1000 | Options |
| 👤 | F1 (131) | Contacts |
| ✉ | F2 (132) | Messages |
| 📷 | CAMERA (27) | Camera |
| 지움 | DEL (67) | Delete a search digit |
| Green | CALL (5) | Call log |
| Red, Home, Recents | not delivered to apps | handled by the phone |

All key meanings live in `app/src/main/java/com/plaintextlab/dumblauncher/Keys.kt`.
Use the **Key tester** to check codes on another phone.

## Build

```
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew lint testDebugUnitTest
```
