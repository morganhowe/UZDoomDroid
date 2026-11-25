#!/bin/sh
# Fetches the third-party sources needed by the Android build into android/deps.
# Safe to run repeatedly; existing checkouts are left alone.
set -eu

SDL_TAG="release-2.32.8"
OPENAL_TAG="1.24.3"
# Audio decoding (FLAC/Ogg/Opus/WAV via libsndfile, MP3 via mpg123)
OGG_TAG="v1.3.5"
VORBIS_TAG="v1.3.7"
FLAC_TAG="1.4.3"
OPUS_TAG="v1.5.2"
SNDFILE_TAG="1.2.2"
MPG123_TAG="v1.31.3-SDL"   # SDL's mirror of mpg123 with its CMake port
# Header-only lib FluidSynth (inside ZMusic) would otherwise download at configure time.
# Keep in sync with GCEM_REVISION in libraries/ZMusic/thirdparty/fluidsynth/cmake_admin/FindGCEM.cmake
GCEM_REV="012ae73c6d0a2cb09ffe86475f5c6fba3926e200"

DEPS="$(cd "$(dirname "$0")" && pwd)/deps"
mkdir -p "$DEPS"

fetch() { # name url tag [file that marks a complete checkout]
	if [ -f "$DEPS/$1/${4:-CMakeLists.txt}" ]; then
		echo "$1: already present"
	else
		rm -rf "$DEPS/$1"
		git -c advice.detachedHead=false clone --depth 1 --branch "$3" "$2" "$DEPS/$1"
	fi
}

fetch SDL         https://github.com/libsdl-org/SDL.git     "$SDL_TAG"
fetch openal-soft https://github.com/kcat/openal-soft.git   "$OPENAL_TAG"
fetch ogg         https://github.com/xiph/ogg.git           "$OGG_TAG"
fetch vorbis      https://github.com/xiph/vorbis.git        "$VORBIS_TAG"
fetch flac        https://github.com/xiph/flac.git          "$FLAC_TAG"
fetch opus        https://github.com/xiph/opus.git          "$OPUS_TAG"
fetch libsndfile  https://github.com/libsndfile/libsndfile.git "$SNDFILE_TAG"

if [ -f "$DEPS/gcem/include/gcem.hpp" ]; then
	echo "gcem: already present"
else
	rm -rf "$DEPS/gcem"
	git init -q "$DEPS/gcem"
	git -C "$DEPS/gcem" fetch -q --depth 1 https://github.com/kthohr/gcem.git "$GCEM_REV"
	git -C "$DEPS/gcem" -c advice.detachedHead=false checkout -q FETCH_HEAD
fi

fetch mpg123      https://github.com/libsdl-org/mpg123.git  "$MPG123_TAG" ports/cmake/CMakeLists.txt

# Local changes to the fetched sources (android/patches).
PATCHES="$(cd "$(dirname "$0")" && pwd)/patches"
apply_patch() { # dir patch
	if git -C "$1" apply --reverse --check "$2" 2>/dev/null; then
		echo "$(basename "$2"): already applied"
	else
		git -C "$1" apply "$2"
		echo "$(basename "$2"): applied"
	fi
}
apply_patch "$DEPS/SDL" "$PATCHES/sdl-plain-text-input.patch"
