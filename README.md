# dumb-launcher

A keypad-first Android launcher for flip phones, built for the **ALT MIVE Style Folder 2 (AT-M140)**.
Everything works from the keys; touch is only a fallback.

## Screens

| Screen | Keys |
|---|---|
| **Home**: big clock at the top; icons with counts for missed calls, messages and other apps; earphone icon; music widget | ● OK → All apps · 0-9 → your 10 number-key apps · ▲▼◀▶ → your 4 shortcut apps · 📞 → call log · hold ● → Settings · 👤 ✉ 📷 → Contacts, Messages, Camera (any screen) |
| **All apps**: A–Z grid (or list) | just type on the keypad to search (T9: 2-2-6 → Camera) · 지움 deletes a digit · ↩ clears the search · hold ● → options |
| **Key tester** | shows the code of every key; press ↩ twice to exit |

`*` and `#` are left to the phone's own software.

The selected icon zooms in, Nokia style.

**Settings** (hold ● on Home, or "Launcher settings" in All apps):

| Setting | Choices |
|---|---|
| Wallpaper from gallery | pick a picture → home screen, lock screen or both |
| Wallpaper from system picker | the phone's own wallpaper chooser |
| Show wallpaper on | Home screen only · All launcher screens · Off (black) |
| App drawer background | Blurred wallpaper · See-through wallpaper · Black |
| Refresh blurred wallpaper | re-makes the blur after changing wallpaper outside the launcher |
| Wallpaper dimming | 0 / 25 / 50 / 75 % (keeps text readable) |
| Hide notification bar | On / Off (swipe down to peek) |
| Show arrow-key apps on Home | On / Off (D-pad cross of the ▲▼◀▶ app icons; hidden while the music widget shows) |
| Show notifications on Home | On / Off |
| Music widget on Home | On / Off. Hold ◀ previous · hold ▼ play/pause · hold ▶ next |
| Notification access | needed for other apps' icons and for song title / album art. Android Go may not allow it; calls, SMS and basic music controls work without it |
| Highlight colour | 10 colours |
| Animations | On / Off (screen transitions, drawer zoom/pop-in, list cascade, highlight fade) |
| Zoom selected icon | On / Off |
| Icon pack | any installed ADW / Nova / Apex-format pack, or None |
| Icon style (no pack) | Clean (no white box) / Original |
| Home ▲▼◀▶ shortcuts | pick an app for each |
| Dock on Home (keys 1-5) | On / Off. Bottom row showing the apps on keys 1-5 |
| Dock apps | per key: your own pick, or Automatic (your most-opened apps) |
| Forget most-used history | resets the automatic dock |
| Hidden apps | apps hidden with hold ● → *Hide from launcher*; pick one to show it again |
| Number key shortcuts (0-9) | pick an app, shortcut or activity for each digit |
| App drawer layout | Grid / List |
| Key tester, Default launcher, Phone settings | |

**Shortcuts and activities.** In All apps, select an app → hold ● → **Shortcuts…** (the actions other
launchers show when you hold an icon) or **Activities…** (the app's individual screens). Pick one and
choose where it goes: the app drawer, a Home arrow key, or a Home number key. "Add to Home screen" from apps
(e.g. a Chrome page) also lands in the app drawer. App shortcuts need Dumb Launcher to be the default launcher.

To change a single app's icon, select it in All apps → hold ● → **Change icon**, then pick
from a grid of the pack's icons (type on the keypad to search icon names). **Reset icon** undoes it.

## Install

1. Download `app-debug.apk` (from the **Build APK** workflow artifacts on GitHub, or a local build).
2. Install it on the phone (allow "install unknown apps").
3. Press Home and choose **Dumb Launcher**, or go to Options → *Choose default launcher*.
4. Allow call log and SMS access so Home can show missed calls and new messages.

## Key codes (measured on AT-M140)

| Key | Code | Use |
|---|---|---|
| ☆ | F3 (133) | Settings / Options |
| `*` | STAR or vendor 1000 | not used (belongs to the phone) |
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

## Releasing

Release APKs must always be signed with the same key, or Android refuses to install updates.
The key is **not** in this repository. Add it once as repository secrets
(Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | the keystore file, base64-encoded (`base64 -w0 release.keystore`) |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key password |

To release, bump `versionCode` and `versionName` in `app/build.gradle.kts` and push. The **Release**
workflow notices the new version, builds the signed APK, creates the `v<version>` tag and publishes a
GitHub release with the APK attached. Pushes that don't change the version do nothing.
If the secrets were missing when a version was pushed, add them and re-run the workflow.

Local signed builds: put the same four values (with `SIGNING_KEYSTORE=<path>`) in an untracked
`keystore.properties`, then run `./gradlew assembleRelease`.
