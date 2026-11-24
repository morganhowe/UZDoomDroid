# Android build setup. Included from the top-level CMakeLists.txt when the
# Android NDK toolchain is active (normally via android/app/build.gradle).
#
# Expected cache variables:
#   UZDOOM_ANDROID_DEPS  directory containing the SDL2 and openal-soft sources
#                        (fetched by android/fetch-deps.sh)
#   IMPORT_EXECUTABLES   ImportExecutables.cmake from android/hosttools

if( NOT UZDOOM_ANDROID_DEPS )
	set( UZDOOM_ANDROID_DEPS "${CMAKE_CURRENT_SOURCE_DIR}/android/deps" )
endif()
set( UZDOOM_ANDROID_DEPS "${UZDOOM_ANDROID_DEPS}" CACHE PATH "Third-party sources for the Android build" )

if( NOT EXISTS "${UZDOOM_ANDROID_DEPS}/SDL/CMakeLists.txt" OR NOT EXISTS "${UZDOOM_ANDROID_DEPS}/openal-soft/CMakeLists.txt"
	OR NOT EXISTS "${UZDOOM_ANDROID_DEPS}/libsndfile/CMakeLists.txt" OR NOT EXISTS "${UZDOOM_ANDROID_DEPS}/mpg123/ports/cmake/CMakeLists.txt" )
	message( FATAL_ERROR "Android dependencies missing in ${UZDOOM_ANDROID_DEPS}. Run android/fetch-deps.sh first." )
endif()

# pk3s, soundfonts and FM banks are written here and picked up by Gradle as assets.
if( UZDOOM_ENGINE_DATA_DIR )
	set( ZDOOM_OUTPUT_DIR "${UZDOOM_ENGINE_DATA_DIR}/${ANDROID_ABI}" CACHE PATH "" FORCE )
endif()

# Header-only GCEM for FluidSynth (avoids a download during configure).
if( EXISTS "${UZDOOM_ANDROID_DEPS}/gcem/include/gcem.hpp" )
	set( GCEM_INCLUDE_DIR "${UZDOOM_ANDROID_DEPS}/gcem/include" CACHE PATH "" FORCE )
endif()

# Everything ends up inside libmain.so, so all static code must be PIC.
set( CMAKE_POSITION_INDEPENDENT_CODE ON )

# Sensible defaults for the platform (still overridable from the command line).
set( FORCE_NO_LTO ON CACHE BOOL "LTO is slow and memory hungry with the NDK" )
set( HAVE_VULKAN ON CACHE BOOL "Vulkan backend (falls back to OpenGL ES when unsupported)" )
set( VULKAN_USE_XLIB OFF CACHE BOOL "" FORCE )
set( HAVE_GLES2 ON CACHE BOOL "OpenGL ES backend" )
set( ENABLE_IWYU OFF CACHE BOOL "" )
set( SEND_ANON_STATS OFF CACHE BOOL "" )
set( NO_OPENMP ON CACHE BOOL "" )

# Bundled FreeType: no bzip2-compressed fonts, and nothing gets installed on Android
# (its install(EXPORT) otherwise trips over the internal bz2 target).
set( FT_DISABLE_BZIP2 ON CACHE BOOL "" FORCE )
set( SKIP_INSTALL_ALL TRUE )

# ---------------------------------------------------------------------------
# SDL2 (shared, as required by SDLActivity)
# ---------------------------------------------------------------------------
set( SDL_SHARED ON CACHE BOOL "" FORCE )
set( SDL_STATIC OFF CACHE BOOL "" FORCE )
set( SDL_TEST OFF CACHE BOOL "" FORCE )
set( SDL2_DISABLE_INSTALL ON CACHE BOOL "" FORCE )
set( SDL2_DISABLE_UNINSTALL ON CACHE BOOL "" FORCE )
add_subdirectory( "${UZDOOM_ANDROID_DEPS}/SDL" "${CMAKE_BINARY_DIR}/deps/SDL" EXCLUDE_FROM_ALL )

# ---------------------------------------------------------------------------
# OpenAL Soft (shared; the engine dlopen()s libopenal.so at runtime)
# ---------------------------------------------------------------------------
set( LIBTYPE SHARED )
set( ALSOFT_UTILS OFF CACHE BOOL "" FORCE )
set( ALSOFT_EXAMPLES OFF CACHE BOOL "" FORCE )
set( ALSOFT_TESTS OFF CACHE BOOL "" FORCE )
set( ALSOFT_INSTALL OFF CACHE BOOL "" FORCE )
set( ALSOFT_INSTALL_CONFIG OFF CACHE BOOL "" FORCE )
set( ALSOFT_INSTALL_HRTF_DATA OFF CACHE BOOL "" FORCE )
set( ALSOFT_INSTALL_AMBDEC_PRESETS OFF CACHE BOOL "" FORCE )
set( ALSOFT_INSTALL_EXAMPLES OFF CACHE BOOL "" FORCE )
set( ALSOFT_INSTALL_UTILS OFF CACHE BOOL "" FORCE )
set( ALSOFT_NO_CONFIG_UTIL ON CACHE BOOL "" FORCE )
set( ALSOFT_EMBED_HRTF_DATA ON CACHE BOOL "" FORCE )
set( ALSOFT_BACKEND_OBOE OFF CACHE BOOL "" FORCE )
set( ALSOFT_REQUIRE_OPENSL ON CACHE BOOL "" FORCE ) # fail loudly instead of shipping a silent build
add_subdirectory( "${UZDOOM_ANDROID_DEPS}/openal-soft" "${CMAKE_BINARY_DIR}/deps/openal-soft" EXCLUDE_FROM_ALL )

# ---------------------------------------------------------------------------
# libsndfile (FLAC/Ogg/Opus/WAV) and libmpg123 (MP3), dlopen()ed by ZMusic
# ---------------------------------------------------------------------------
include( "${CMAKE_CURRENT_LIST_DIR}/AndroidAudio.cmake" )
