# Audio decoding libraries for the Android build: libsndfile (WAV, FLAC,
# Ogg Vorbis, Opus) and libmpg123 (MP3).
#
# ZMusic dlopen()s these at runtime (DYN_SNDFILE / DYN_MPG123), so they are
# built as shared libraries: libsndfile.so (with ogg/vorbis/flac/opus linked in
# statically) and libmpg123.so. Defines the target UZDOOM_AUDIO_LIBS that
# depends on both, so they get built and packaged.
#
# Expects UZDOOM_ANDROID_DEPS to point at the sources from android/fetch-deps.sh.

# Nothing is installed on Android, and the bundled libraries' install(EXPORT)
# rules clash with each other (FLAC and mpg123 share the "targets" export set).
set( CMAKE_SKIP_INSTALL_RULES ON )

set( _uz_audio_bin "${CMAKE_BINARY_DIR}/deps/audio" )
set( _uz_saved_shared ${BUILD_SHARED_LIBS} )
set( _uz_saved_testing ${BUILD_TESTING} )
set( BUILD_TESTING OFF )

# ---------------------------------------------------------------------------
# Codecs, static and position independent (they end up inside libsndfile.so)
# ---------------------------------------------------------------------------
set( BUILD_SHARED_LIBS OFF )
set( CMAKE_POSITION_INDEPENDENT_CODE ON )

set( INSTALL_DOCS OFF CACHE BOOL "" FORCE )
set( INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "" FORCE )
set( INSTALL_CMAKE_PACKAGE_MODULE OFF CACHE BOOL "" FORCE )
add_subdirectory( "${UZDOOM_ANDROID_DEPS}/ogg" "${_uz_audio_bin}/ogg" EXCLUDE_FROM_ALL )

# libvorbis looks for Ogg with find_package() unless OGG_* are preset.
set( OGG_INCLUDE_DIR "${UZDOOM_ANDROID_DEPS}/ogg/include;${_uz_audio_bin}/ogg/include" CACHE STRING "" FORCE )
set( OGG_LIBRARY ogg CACHE STRING "" FORCE )
set( OGG_INCLUDE_DIRS ${OGG_INCLUDE_DIR} )
set( OGG_LIBRARIES ogg )
add_subdirectory( "${UZDOOM_ANDROID_DEPS}/vorbis" "${_uz_audio_bin}/vorbis" EXCLUDE_FROM_ALL )

set( BUILD_CXXLIBS OFF CACHE BOOL "" FORCE )
set( BUILD_PROGRAMS OFF CACHE BOOL "" FORCE )
set( BUILD_EXAMPLES OFF CACHE BOOL "" FORCE )
set( BUILD_DOCS OFF CACHE BOOL "" FORCE )
set( BUILD_UTILS OFF CACHE BOOL "" FORCE )
set( INSTALL_MANPAGES OFF CACHE BOOL "" FORCE )
set( INSTALL_PKGCONFIG_MODULES OFF CACHE BOOL "" FORCE )
set( INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "" FORCE )
set( WITH_OGG ON CACHE BOOL "" FORCE )
set( WITH_FORTIFY_SOURCE OFF CACHE BOOL "" FORCE )
set( WITH_STACK_PROTECTOR OFF CACHE BOOL "" FORCE )
add_subdirectory( "${UZDOOM_ANDROID_DEPS}/flac" "${_uz_audio_bin}/flac" EXCLUDE_FROM_ALL )

set( OPUS_BUILD_PROGRAMS OFF CACHE BOOL "" FORCE )
set( OPUS_BUILD_TESTING OFF CACHE BOOL "" FORCE )
set( OPUS_INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "" FORCE )
set( OPUS_INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "" FORCE )
add_subdirectory( "${UZDOOM_ANDROID_DEPS}/opus" "${_uz_audio_bin}/opus" EXCLUDE_FROM_ALL )

# libsndfile includes <opus/opus.h>; the Opus source tree has include/opus.h.
file( COPY "${UZDOOM_ANDROID_DEPS}/opus/include/" DESTINATION "${_uz_audio_bin}/opus-include/opus" )
target_include_directories( opus INTERFACE "$<BUILD_INTERFACE:${_uz_audio_bin}/opus-include>" )

# libsndfile finds its codecs as CMake packages. Point it at small config
# files that refer to the targets built above.
foreach( _t vorbis vorbisenc vorbisfile )
	if( NOT TARGET Vorbis::${_t} )
		add_library( Vorbis::${_t} ALIAS ${_t} )
	endif()
endforeach()
set( _uz_cfg "${_uz_audio_bin}/configs" )
foreach( _pkg Ogg Vorbis FLAC Opus )
	file( WRITE "${_uz_cfg}/${_pkg}/${_pkg}Config.cmake"
		"# Provided by UZDoomDroid: targets are built in-tree.\nset( ${_pkg}_FOUND TRUE )\nset( ${_pkg}_Enc_FOUND TRUE )\n" )
	file( WRITE "${_uz_cfg}/${_pkg}/${_pkg}ConfigVersion.cmake"
		"set( PACKAGE_VERSION 99.0 )\nset( PACKAGE_VERSION_COMPATIBLE TRUE )\n" )
	set( ${_pkg}_DIR "${_uz_cfg}/${_pkg}" CACHE PATH "" FORCE )
endforeach()

# ---------------------------------------------------------------------------
# libsndfile (shared)
# ---------------------------------------------------------------------------
set( BUILD_SHARED_LIBS ON )
set( CMAKE_FIND_PACKAGE_PREFER_CONFIG ON )
set( ENABLE_EXTERNAL_LIBS ON CACHE BOOL "" FORCE )
set( ENABLE_MPEG OFF CACHE BOOL "" FORCE )         # MP3 goes through libmpg123 in ZMusic
set( ENABLE_CPACK OFF CACHE BOOL "" FORCE )
set( ENABLE_PACKAGE_CONFIG OFF CACHE BOOL "" FORCE )
set( INSTALL_PKGCONFIG_MODULE OFF CACHE BOOL "" FORCE )
set( BUILD_REGTEST OFF CACHE BOOL "" FORCE )
add_subdirectory( "${UZDOOM_ANDROID_DEPS}/libsndfile" "${_uz_audio_bin}/libsndfile" EXCLUDE_FROM_ALL )
unset( CMAKE_FIND_PACKAGE_PREFER_CONFIG )
get_directory_property( _uz_xiph DIRECTORY "${UZDOOM_ANDROID_DEPS}/libsndfile" DEFINITION HAVE_EXTERNAL_XIPH_LIBS )
if( NOT _uz_xiph )
	message( FATAL_ERROR "libsndfile was configured without FLAC/Vorbis/Opus support" )
endif()

# ---------------------------------------------------------------------------
# libmpg123 (shared)
# ---------------------------------------------------------------------------
set( BUILD_LIBOUT123 OFF CACHE BOOL "" FORCE )
add_subdirectory( "${UZDOOM_ANDROID_DEPS}/mpg123/ports/cmake" "${_uz_audio_bin}/mpg123" EXCLUDE_FROM_ALL )

add_custom_target( UZDOOM_AUDIO_LIBS )
add_dependencies( UZDOOM_AUDIO_LIBS sndfile libmpg123 )

set( BUILD_SHARED_LIBS ${_uz_saved_shared} )
set( BUILD_TESTING ${_uz_saved_testing} )
