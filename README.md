# dumb-launcher

A keypad-first Android launcher for flip phones, built for the **ALT MIVE Style Folder 2 (AT-M140)**.
Everything works from the keys; touch is only a fallback.

## Screens

| Screen | Keys |
|---|---|
| **Start** (Windows Phone look, the default): your own tiles: apps, Clock, Calendar, Weather, Photos, Music (album art with ⏮ ⏯ ⏭; tap it for the music popup), People (contact photos, 3×3). Nothing is added or reordered on its own | ▲▼◀▶ select a tile · ● OK opens it · hold ● → tile menu (Move, Resize, Colour with previews, Opens… for live tiles, Unpin, Add a live tile, music controls, Launcher settings) · in Move: ◀▶ ▲▼ move, ● done · 1-9 → your number-key apps · 0 → All apps · touch: hold a tile and drag (the others slide out of the way), hold without moving for its menu · swipe left → All apps · swipe down → notifications · swipe up at the bottom → All apps |
| **Home** (Android look): big clock at the top; icons with counts for missed calls, messages and other apps; earphone icon; music widget | ● OK or 0 → All apps · 1-9 → your number-key apps · ▲▼◀▶ → your 4 shortcut apps · 📞 → call log · hold ● → Settings · 👤 ✉ 📷 → Contacts, Messages, Camera (any screen) · touch: swipe up or left → All apps, swipe down → notifications, tap the clock → Clock app, tap a dock / D-pad icon → that app, tap the call or message icon → call log / Messages |
| **All apps**: A–Z or most used first (hold ● → Sort, or Settings), grid or list | just type on the keypad to search (T9: 2-2-6 → Camera), or tap the search box for the touch keyboard · 지움 deletes a digit · ↩ clears the search · hold ● → options |
| **Key tester** | shows the code of every key; press ↩ twice to exit |

`*` and `#` are left to the phone's own software.

The selected icon zooms in, Nokia style.

**Settings** (hold ● on Home, or "Launcher settings" in All apps):

| Setting | Choices |
|---|---|
| Wallpaper from gallery | pick a picture → home screen, lock screen or both |
| Wallpaper from system picker | the phone's own wallpaper chooser |
| Look | **Windows Phone** (default: Start screen of tiles) / **Android** (big clock, icons, dock, arrow-key shortcuts). Settings show only the options for the chosen look |
| Show wallpaper on | Home screen only · All launcher screens · Off (black) |
| App drawer background | Blurred wallpaper · See-through wallpaper · Black |
| Refresh blurred wallpaper | re-makes the blur after changing wallpaper outside the launcher |
| Wallpaper dimming | 0 / 25 / 50 / 75 % (keeps text readable) |
| Hide notification bar | On / Off (swipe down to peek) |
| Clock on Home | On / Off |
| Show arrow-key apps on Home | On / Off (D-pad cross of the ▲▼◀▶ app icons; hidden while the music widget shows) |
| Show notifications on Home | On / Off |
| Music widget on Home | On / Off. Now playing: cover, song, the player's icon and a progress bar. Tap it (or hold ▲) for the **music popup**: big cover, progress, ⏮ ⏯ ⏭; tap the cover to open the player. Hold ◀ previous · hold ▼ play/pause · hold ▶ next · Back hides it until the next song or until playback resumes. Works with any player that shows media controls (YouTube, YouTube Music, Spotify, Auxio, …) |
| Tile colours (Metro) | Each app's own colour (default) / Accent colour |
| Weather tile (Metro) | On / Off. Current temperature and today's range from Open-Meteo (free, no account), for your approximate location rounded to about 1 km; refreshed at most every 30 minutes; tap to update |
| Weather units | °C / °F |
| Photos tile (Metro) | On / Off. A wide tile cycling through your recent photos; tap to open the gallery |
| Notification access | needed for other apps' icons and for song title, album art and progress. Android Go may not allow it; calls, SMS and basic music controls work without it |
| Accent colour | 15 flat colours (Cyan by default): the selection, and the tiles in the Metro style |
| Animations | On / Off (screen transitions, drawer zoom/pop-in, list cascade, highlight fade) |
| Zoom selected icon | On / Off (in the Metro grid the selected tile gets a white frame and lifts slightly) |
| Icon pack | any installed ADW / Nova / Apex-format pack, or None. Apps the pack has no icon for are put on the pack's own backing shape (if the pack has one), so they still match |
| Icon style | Android look only: Clean (no white box) / Original / Glossy tiles. In the Windows Phone look every app is a flat square in the accent colour (or its own colour) with a white glyph |
| Home ▲▼◀▶ shortcuts | pick an app for each |
| Dock on Home (keys 1-5) | On / Off. Bottom row showing the apps on keys 1-5 |
| Dock apps | per key: your own pick, or Automatic (your most-opened apps) |
| Forget most-used history | resets the automatic dock |
| Hidden apps | apps hidden with hold ● → *Hide from launcher*; pick one to show it again |
| Number key shortcuts (1-9) | pick an app, shortcut or activity for each digit |
| App drawer layout | Grid / List |
| Key tester, Default launcher, Phone settings | |

**Shortcuts and activities.** In All apps, select an app → hold ● → **Shortcuts…** (the actions other
launchers show when you hold an icon) or **Activities…** (the app's individual screens). Pick one and
choose where it goes: the app drawer, a Home arrow key, or a Home number key. "Add to Home screen" from apps
(e.g. a Chrome page) also lands in the app drawer. App shortcuts need Dumb Launcher to be the default launcher.

To change a single app's icon, select it in All apps → hold ● → **Change icon**, then pick
from a grid of the pack's icons (type on the keypad to search icon names). **Reset icon** undoes it.

Pin any app to Start from All apps → hold ● → **Pin to Start**. Tiles come in four sizes (small 1x1, medium 2x2, wide
4x2, large 4x4) and any colour: automatic (the app's own), the accent colour, see-through, or one of the palette colours.

To rename an app, select it in All apps → hold ● → **Rename…** and type the new name. The new name
is used everywhere (drawer, T9 search, dock, Settings). **Reset name** (or saving an empty name) goes
back to the app's own name.

## Install

1. Download `app-debug.apk` (from the **Build APK** workflow artifacts on GitHub, or a local build).
2. Install it on the phone (allow "install unknown apps").
3. Press Home and choose **Dumb Launcher**, or go to Options → *Choose default launcher*.
4. Allow call log and SMS access so Home can show missed calls and new messages.

## Key codes (measured on AT-M140)

| Key | Code | Use |
|---|---|---|
| ☆ | F3 (133) | not used |
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
