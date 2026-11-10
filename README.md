# dumb-launcher

A keypad-first Android launcher for flip phones, built for the **ALT MIVE Style Folder 2 (AT-M140)**.
Everything works from the keys; touch is only a fallback.

## Screens

| Screen | Keys |
|---|---|
| **Home**: clock, missed calls, new messages | ● OK → Menu · 0-9 → dialer · ▲▼◀▶ → your 4 shortcut apps · 📞 → call log · hold `*` → vibrate on/off · ☰ or hold ● → Options |
| **Menu**: 3×3 grid | 1-9 open that cell at once · ● open · ☰ or hold ● → change/empty a slot |
| **All apps**: A–Z list | type on the keypad to filter (T9: 2-2-6 → Camera) · 지움 deletes a digit · ↩ clears the search |
| **Key tester** | shows the code of every key; press ↩ twice to exit |

The bottom bar of every screen says what the keys do.

## Install

1. Download `app-debug.apk` (from the **Build APK** workflow artifacts on GitHub, or a local build).
2. Install it on the phone (allow "install unknown apps").
3. Press Home and choose **Dumb Launcher**, or go to Options → *Choose default launcher*.
4. Allow call log and SMS access so Home can show missed calls and new messages.

## First thing to do: run the Key tester

The codes for the ☆, 👤, ✉ and 📷 keys on this phone are unknown. Open **Key tester**,
press each key once and hold it once, and note the name/code shown. Keys that show nothing
are taken by the system. All key meanings live in `app/src/main/java/com/plaintextlab/dumblauncher/Keys.kt`.

## Build

```
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew lint testDebugUnitTest
```
