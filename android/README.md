# UZDoom for Android (controller only)

> [!CAUTION]
> **AI-generated:** this Android port was written by an AI (Anthropic's Claude) and hasn't been reviewed by a human expert.
> It is **unofficial** and not supported by the UZDoom team, which does not accept AI-generated code.
> Never submit it to UZDoom, and report problems [here](https://github.com/morganhowe/UZDoomDroid/issues), not upstream.
> See the [main README](../README.md) for details.

An Android port of UZDoom built to be played entirely with a gamepad.
No touch input is needed anywhere: not in the launcher, the game, menus, or error dialogs.
It runs on phones and tablets with a paired controller, Android TV / Google TV boxes, and Android handhelds
(Retroid, AYN Odin, Ayaneo, Logitech G Cloud, and similar).

## What you need

* Android 8.0 (API 26) or newer, 64-bit ARM (`arm64-v8a`). `x86_64` can be added for emulators and Chromebooks.
* OpenGL ES 3.x (2.0 works with reduced features)
* Any controller Android recognises: Xbox, PlayStation, Switch Pro, 8BitDo, the built-in controls on handhelds, etc.
* A game IWAD: `doom.wad`, `doom2.wad`, `heretic.wad`, `hexen.wad`, `strife1.wad`, … or the free
  [Freedoom](https://freedoom.github.io/) `freedoom1.wad` / `freedoom2.wad`. No game data is bundled.

## Building

### Releases

1. Bump `versionCode` and `versionName` in `app/build.gradle` and add `release-notes/v<version>.md`, then push.
2. On GitHub: **Releases → Draft a new release**, type the tag `v<version>` (it must match `versionName`),
   pick the `android-port` branch, and click **Publish release**.

The **Android** workflow then builds the APK and attaches `UZDoomDroid-<tag>.apk` and a SHA-256 checksum. It also
replaces the release title and notes with `release-notes/<tag>.md`.

### On GitHub (easiest)

Push this branch to your fork. The **Android** workflow (`.github/workflows/android.yml`) builds a
release APK and attaches it as the `uzdoomdroid-android` artifact on the run.

### Locally

Requirements: JDK 17+, Android SDK with NDK **27.2.12479018** and CMake **3.22.1**
(`sdkmanager "ndk;27.2.12479018" "cmake;3.22.1"`), plus a host C/C++ compiler, CMake and Python 3.

```sh
sh android/fetch-deps.sh          # SDL2 2.32.8, OpenAL Soft 1.24.3, GCEM  -> android/deps/
cd android
./gradlew assembleRelease         # -> app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

To build more ABIs: `./gradlew assembleRelease -Puzdoom.abis=arm64-v8a,x86_64`.

### Signing

Releases are signed with a permanent key so updates install over each other. CI reads it from two repository secrets
(Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | The PKCS#12 keystore, base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | Its password (the key alias is `uzdoomdroid`) |

A release build fails if the secrets are missing, or if the APK's certificate doesn't match
`android/signing-cert-sha256.txt`. Builds in this repository (including test builds from Actions) are signed with the
release key too, so they install over a release. Builds without the secrets, such as from a fork, use a temporary
debug key and print a warning.

To sign locally, set `UZDOOM_KEYSTORE` (path to the keystore) and `UZDOOM_KEYSTORE_PASSWORD` before running Gradle.
Keep a backup of the keystore: **if it's lost, no future version can update an installed release.**

### How the build fits together

1. `android/hosttools` builds `lemon`, `re2c` and `zipdir` for the build machine and writes
   `ImportExecutables.cmake`. The cross build uses these to generate parsers and pack the pk3s.
2. Gradle runs the top-level `CMakeLists.txt` with the NDK. With `ANDROID` set,
   `cmake/Android.cmake` adds SDL2 and OpenAL Soft from `android/deps`, and the engine is built
   as `libmain.so` (the library SDLActivity loads).
3. The pk3s, soundfont and FM banks the build produces become APK assets. On first launch they
   are extracted to the app's internal storage, which the engine uses as its program directory.

## Installing games and mods

The launcher shows a **Game** list (IWADs) and a **Mods** list. Add files either way:

* **Import (Y button):** opens the Android file picker, which you can navigate with the D-pad.
  Pick one or more files and they are copied in.
* **USB:** connect to a PC and copy files into
  `Android/data/io.github.morganhowe.uzdoomdroid/files/wads/`

A `.wad` with an `IWAD` header (and `.ipk3` / `.iwad` files) appears under **Game**. Everything else
(`.wad` PWADs, `.pk3`, `.pk7`, `.zip`, `.deh`, `.bex`) appears under **Mods**. Selected mods load in
alphabetical order.

Advanced: put extra command-line arguments in `Android/data/io.github.morganhowe.uzdoomdroid/files/args.txt`
(for example `+map MAP01` or `-warp 1 1`). Lines starting with `#` are ignored.

Everything the game writes is in `Android/data/io.github.morganhowe.uzdoomdroid/files/`, so you can back it up over USB:

| Folder | Contents |
|---|---|
| `wads/` | Games and mods |
| `savegames/` | Saved games |
| `screenshots/` | Screenshots |
| `config/uzdoom/` | `uzdoom.ini` (all settings and controls) |

Versions 0.0.1 and 0.0.2 kept saves and settings in hidden folders (`.local/share/games/uzdoom/` and
`.config/uzdoom/`) that Android doesn't show over USB. Later versions move them to the folders above automatically
when the game starts. If a PC doesn't show newly saved files right away, go back to the launcher once (it asks
Android to index them) or reconnect the USB cable.

**Pausing:** turning the screen off, pressing Home or switching apps pauses the game and its sound. When you come
back, the main menu is open. Press B to carry on.

## Controls

**Launcher**

| Button | Action |
|---|---|
| D-pad / left stick | Move |
| A | Select game / toggle mod / press button |
| B | Quit |
| START | Play |
| Y | Import files |
| L1 / R1 | Jump to the Game / Mods list |
| SELECT | Licenses |

**In game** (UZDoom's default gamepad bindings, all changeable in the controls menu)

| Button | Action |
|---|---|
| Left stick | Move / strafe |
| Right stick | Turn / look up and down (looking needs freelook, see below) |
| RT / LT | Fire / alt-fire |
| A | Use / open |
| B | Crouch |
| X | Reload |
| Y | Jump |
| LB / RB | Previous / next weapon |
| Right stick click | Zoom |
| D-pad left / right / down | Inventory prev / next / use |
| D-pad up | Weapon slot 1 |
| BACK / SELECT | Automap |
| Guide / Home button | Pause |
| START | Main menu |
| Android Back key (TV remote) | Escape (open / close menu) |

On the automap, LB / RB zoom, the D-pad pans, X toggles follow mode, and A / B set or clear marks.

**Changing controls.** In *Options → Customize Controls*, press A on an action and then the button to bind
(START cancels), or press X to clear its bindings.

**Extra buttons.** Handhelds with additional buttons (such as the extra shoulder or back buttons on Retroid
devices) can bind them too, the same way. They show up
as *Joy1* to *Joy20*; the first press of each one also prints its name in the console. If a button does nothing
at all, it's probably assigned to a system function: check your device's own button-mapping settings and set it
to a gamepad button (for example "Button 1" or "C").

Jumping and freelook are **off by default**, like the original games. To use them, set *Options → Gameplay Options →
Allow jump / Allow freelook* to On or Default. This resets each time the game starts.

**Dual screens.** On handhelds with a second screen (such as the AYN Thor), the game plays on the main screen
and the second screen shows a live map that follows you. *Options → Automap Options* has *Map on second screen*
to turn it off, and *Second screen map zoom*. The second-screen map draws the map lines, you and keys; it leaves
out extras such as mark numbers, thing sprites and textured floors. The normal automap still works on the main
screen.

The automap shows your device's battery level in the top right corner. To hide it, turn off *Options →
Automap Options → Show battery level*.

The game renders at your screen's full resolution, with sharp, unfiltered pixels. For smoother (filtered)
textures, change *Options → Display Options → Texture Options → Texture Filter mode*; for a lower rendering
resolution (faster on weak phones), change *Display Options → Video Mode → Resolution scale*.
Menu text is sized for small screens on Android. Change it in *HUD Options → Scaling Options → Menu text scale*.
Menus are navigated with the D-pad. Use A to confirm and B to go back. Naming a save shows
UZDoom's on-screen character grid, so no keyboard is needed.
Rumble works on controllers that support it. Strength, dead zones and sensitivity are in the
controller options menu.

## Licenses

Press **SELECT** in the launcher, or choose **Licenses**, to see the GPL notice, the source link, the modification notice,
and the full license text for every bundled component. Gradle copies these texts into the APK from the source tree and
`android/deps`.

## Platform notes and limitations

* **Renderer:** Vulkan where the device supports it, otherwise OpenGL ES (the software renderer is also available).
  Vulkan is UZDoom's full-featured backend, the same as on desktop, and the best choice for demanding mods. Its
  device needs: anisotropic filtering, fragment stores and atomics, multi-draw-indirect and independent blending.
  Recent Snapdragon (Adreno 6xx and later) GPUs have all four; some Mali and PowerVR GPUs don't, and fall back to
  OpenGL ES automatically.
  * To force OpenGL ES, set *Display Options → Video mode → Preferred rendering API* to **OpenGL ES** and restart.
    On Android, the "OpenGL" and "Vulkan" settings both mean "Vulkan if supported".
  * If Vulkan ever crashes before showing a frame (a GPU driver bug), the next start switches to OpenGL ES by itself
    and says so in the console.
  * Android throws away the window surface while the app is in the background; the Vulkan renderer rebuilds it when
    you return.
* **Video cutscenes:** VP8/VP9 (`.ivf`) movies are disabled because libvpx isn't built for Android.
  Other movie formats (ANM, MVE, Smacker) still play.
* **Music and sound:** everything plays: MIDI (FluidSynth, OPL, OPN, Timidity, WildMidi), tracker and
  game-music-emu formats, plus WAV/FLAC/Ogg Vorbis/Opus (via `libsndfile.so`) and MP3 (via `libmpg123.so`),
  both built from source by `cmake/AndroidAudio.cmake`.
* Each game session runs in its own process (`:game`). Quitting the game returns to the launcher.
  Choosing "Restart" in the error dialog, or using the restart command, relaunches with the
  same files.
* Engine output goes to logcat under the `UZDoom` tag (`adb logcat -s UZDoom`).
