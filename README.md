> [!CAUTION]
> ## ⚠️ AI-GENERATED CODE: READ THIS FIRST
>
> **The Android port in this repository was written by an AI (Anthropic's Claude), not by a human developer.**
> That includes all of the Android-specific code, the build scripts, the launcher app, the CI workflow, the app icon,
> and this README section. The changes are listed under [What the AI wrote](#what-the-ai-wrote) below.
>
> - **This is not an official UZDoom release.** It is not made, reviewed, endorsed or supported by the UZDoom team.
> - **UZDoom does not accept AI-generated code or art.** Its [contribution guidelines](CONTRIBUTING.md) ban it outright,
>   and undisclosed AI use gets contributors banned from the whole organization.
>   **Never open a pull request from this fork to UZDoom**, and don't copy code from it into one.
> - **Don't report problems with this port to UZDoom.** Use this repository's
>   [issues](https://github.com/morganhowe/UZDoomDroid/issues) instead.
> - **Lightly tested.** No human expert has reviewed the code. It has been built by CI and tried on one device. Expect bugs.
>
> The engine itself is UZDoom, written by its human developers and contributors (see the original README below).

> [!NOTE]
> **From the repository owner:**
> This is a vibe-coded port of UZDoom to Android. I do not expect to keep this maintained; I only wanted to test it out. All credit should go to the UZDoom team, I can take no credit for anything in this project.

# UZDoomDroid

An unofficial, controller-only Android port of [UZDoom](https://github.com/UZDoom/UZDoom).
You can play entirely with a gamepad: the launcher, the game, the menus and the error dialogs all work without touch.
It runs on phones and tablets with a controller, Android TV / Google TV, and Android handhelds.

## Status

| | |
|---|---|
| Build | [![Android](https://github.com/morganhowe/UZDoomDroid/actions/workflows/android.yml/badge.svg?branch=android-port)](https://github.com/morganhowe/UZDoomDroid/actions/workflows/android.yml) |
| Maintenance | Not maintained. This was an experiment |
| Requires | Android 8.0+, 64-bit ARM, OpenGL ES 2.0+ (3.x recommended), a game controller |
| Game data | Not included. Bring your own IWAD (e.g. `doom2.wad`) or use the free [Freedoom](https://freedoom.github.io/) |

## Getting the app

1. Open the [Android workflow runs](https://github.com/morganhowe/UZDoomDroid/actions/workflows/android.yml)
   and click the newest run with a green check.
2. Under **Artifacts**, download **uzdoomdroid-android** (you need to be signed in to GitHub). Unzip it to get the `.apk`.
3. Copy the `.apk` to your device, open it, and allow installs from that app when Android asks.

The APK is signed with a debug key. It's fine for personal use, but not for redistribution.

## Playing

1. Open **UZDoomDroid**, connect a controller, and press **Y** to import your `.wad` with the system file picker
   (or copy files over USB to `Android/data/io.github.morganhowe.uzdoomdroid/files/wads/`).
2. Pick the game, optionally tick some mods, and press **START**.

Full controller maps, file locations, build instructions and known limitations are in
**[android/README.md](android/README.md)**.

## What the AI wrote

Everything that makes this an Android port:

- `android/`: Gradle project, the controller-driven launcher, the game activity, the host-tools build, the dependency fetch script
- `cmake/Android.cmake`: the Android build setup (SDL2, OpenAL Soft, platform defaults)
- `.github/workflows/android.yml`: the APK build
- Android-only changes, marked `__ANDROID__` or `ANDROID`, in:
  `CMakeLists.txt`, `src/CMakeLists.txt`, `libraries/ZWidget/CMakeLists.txt`,
  `src/common/platform/posix/sdl/{i_main,i_system,i_input,sdlglvideo}.cpp`, `src/common/audio/sound/oalsound.cpp`
- Changes that make libvpx optional (it's guarded by `USE_LIBVPX`) in `src/common/cutscenes/movieplayer.cpp` and `src/common/textures/animtexture.cpp`

To see every change, [compare this fork against upstream UZDoom](https://github.com/UZDoom/UZDoom/compare/trunk...morganhowe:UZDoomDroid:android-port).

## Modification notice

As required by the GNU GPL v3, section 5(a): **this is a modified version of UZDoom.**

- **Based on:** UZDoom commit [`678ed12`](https://github.com/UZDoom/UZDoom/commit/678ed12027fec6257a08bfcdbabba06bfcebf8de)
- **Modified:** starting 2026-11-22, by Claude (AI) at the request of the repository owner
- **What changed:** Android support. The files are listed under [What the AI wrote](#what-the-ai-wrote), and every change is dated in the commit history.

The app shows the same notice, the source link and every bundled license under **Licenses** in the launcher (press **SELECT**).

## License

Same as UZDoom: **GPLv3 or later**. See the legal section of the original README below and the [licenses](docs/licenses) folder.
Third-party libraries fetched at build time keep their own licenses: SDL2 (zlib), OpenAL Soft (GNU LGPL; shipped as a
separate `libopenal.so`), and GCEM (Apache 2.0).

The game-content packs (`brightmaps.pk3`, `game_support.pk3`, `game_widescreen_gfx.pk3`) are based on original game
content. They may be shared for free but **may not be sold**, so don't charge for this app or bundle it with anything paid.
No game data (IWADs) is included or may be distributed with it.

---

<details>
<summary><b>Original UZDoom README</b></summary>

<div align="center">

[ <img src="branding/misc/logo-bg.svg" alt="UZDoom Icon" style="height: 8em; width: 100%" /> ][repo]

</div>

## Welcome to UZDoom!

[![Continuous Integration][badge_git]][status_git]
[![Engine Translation status][badge_trans]][status_trans]
[![Game Translation status][badge_trans_games]][status_trans_games]

**UZDoom** is a modern, feature-rich source port for the classic game **DOOM**.

A continuation of [ZDoom][zdoom] and [GZDoom][gzdoom], UZDoom enhances the original DOOM engine, providing advanced features like:

* High-Resolution Graphics
* Dynamic lighting
* 3D Floors
* Extensive Modding Support
* Support for modern OpenGL and Vulkan renderers

UZDoom is **free and open-source software**, built and maintained by a dedicated community of developers and enthusiasts.

## 🙏 Acknowledgments

UZDoom would not be possible without the foundational work of many people. We extend our immense gratitude to:

* **id Software** for creating the original DOOM and releasing its source code.
* **Marisa Heit** for her foundational work on ZDoom, and **Christoph Oelckers** for his work on GZDoom.
* The countless modders, mappers, and artists in the DOOM community who continue to create amazing content.
* All the contributors who have submitted code, reported bugs, and helped improve the project over the years.

The **UZDoom Icon** was designed by **Carlos "Cardboard Marty" Sanchez**, copyrighted to the UZDoom Team, and licensed under **Creative Commons BY-SA 4.0**.

See the [CONTRIBUTORS](CONTRIBUTORS) file for a full list of code contributors.

## 📄 Legal

UZDoom is licensed under the **GNU General Public License (GPL) version 3 or any later version (GPLv3+)**.

This program is distributed in the hope that it will be useful, but **WITHOUT ANY WARRANTY**. See the GNU General Public License for more details.

You can view the full license text here: <https://www.gnu.org/licenses/>

**Copyrights:**
* Copyright 1993-1996 id Software
* Copyright 1999-2016 Marisa Heit
* Copyright 2002-2016 Christoph Oelckers
* Copyright 2017-2025 GZDoom Maintainers and Contributors
* Copyright 2025-2026 UZDoom Maintainers and Contributors

## 🌐 Resources

* [Home Page][home]
* [Wiki][wiki]
* [Discord Server][community]
* [Forum][forum]
* [Engine Translation][status_trans]
* [Game Translation][status_trans_games]

### 🛠️ Building UZDoom

To build UZDoom from source, please see UZDoom's GitHub [wiki][gh_wiki] for a full list of dependencies and detailed instructions. Build For [Linux][gh_linux] / [Windows][gh_windows] / [MacOS][gh_apple]

<div align="center">

[ <img src="branding/UZDoom%20Banner.svg" alt="UZDoom Footer" style="height: 8em; width: 100%" /> ][repo]

[<img alt="Translation status" style="width: 49%" src="https://hosted.weblate.org/widget/uzdoom/open-graph.png" />][status_trans]
[<img alt="Translation status" style="width: 49%" src="https://hosted.weblate.org/widget/doom-engine-games/open-graph.png" />][status_trans_games]

</div>

[gzdoom]: https://github.com/ZDoom/gzdoom/
[zdoom]: https://github.com/rheit/zdoom/

[repo]: https://github.com/UZDoom/UZDoom/
[home]: https://zdoom.org/
[wiki]: https://zdoom.org/wiki/
[forum]: https://forum.zdoom.org/
[community]: https://dsc.gg/zdoom

[gh_wiki]: https://github.com/UZDoom/UZDoom/wiki/
[gh_linux]: https://github.com/UZDoom/UZDoom/wiki/Compilation#linux
[gh_windows]: https://github.com/UZDoom/UZDoom/wiki/Compilation#windows
[gh_apple]: https://github.com/UZDoom/UZDoom/wiki/Compilation#macos

[status_git]: https://github.com/UZDoom/UZDoom/actions/workflows/continuous_integration.yml
[badge_git]: https://github.com/UZDoom/UZDoom/actions/workflows/continuous_integration.yml/badge.svg

[badge_trans]: https://hosted.weblate.org/widget/uzdoom/svg-badge.svg
[status_trans]: https://hosted.weblate.org/engage/uzdoom/

[badge_trans_games]: https://hosted.weblate.org/widget/doom-engine-games/svg-badge.svg
[status_trans_games]: https://hosted.weblate.org/engage/doom-engine-games/

</details>
