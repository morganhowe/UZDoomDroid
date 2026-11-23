#!/bin/sh
# Fetches the third-party sources needed by the Android build into android/deps.
# Safe to run repeatedly; existing checkouts are left alone.
set -eu

SDL_TAG="release-2.32.8"
OPENAL_TAG="1.24.3"
# Header-only lib FluidSynth (inside ZMusic) would otherwise download at configure time.
# Keep in sync with GCEM_REVISION in libraries/ZMusic/thirdparty/fluidsynth/cmake_admin/FindGCEM.cmake
GCEM_REV="012ae73c6d0a2cb09ffe86475f5c6fba3926e200"

DEPS="$(cd "$(dirname "$0")" && pwd)/deps"
mkdir -p "$DEPS"

fetch() { # name url tag
	if [ -f "$DEPS/$1/CMakeLists.txt" ]; then
		echo "$1: already present"
	else
		rm -rf "$DEPS/$1"
		git -c advice.detachedHead=false clone --depth 1 --branch "$3" "$2" "$DEPS/$1"
	fi
}

fetch SDL         https://github.com/libsdl-org/SDL.git     "$SDL_TAG"
fetch openal-soft https://github.com/kcat/openal-soft.git   "$OPENAL_TAG"

if [ -f "$DEPS/gcem/include/gcem.hpp" ]; then
	echo "gcem: already present"
else
	rm -rf "$DEPS/gcem"
	git init -q "$DEPS/gcem"
	git -C "$DEPS/gcem" fetch -q --depth 1 https://github.com/kthohr/gcem.git "$GCEM_REV"
	git -C "$DEPS/gcem" -c advice.detachedHead=false checkout -q FETCH_HEAD
fi
