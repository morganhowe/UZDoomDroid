# Builds only the code-generation tools (lemon, re2c, zipdir) for the
# *build machine*. The Android cross build imports them through the
# generated ImportExecutables.cmake (see IMPORT_EXECUTABLES in the root
# CMakeLists.txt).
#
#   cmake -S android/hosttools -B build-host && cmake --build build-host
#
cmake_minimum_required( VERSION 3.16 )
project( UZDoomHostTools C CXX )

set( UZDOOM_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../.." )
get_filename_component( UZDOOM_ROOT "${UZDOOM_ROOT}" ABSOLUTE )

if( NOT CMAKE_BUILD_TYPE )
	set( CMAKE_BUILD_TYPE Release )
endif()

set( CMAKE_CXX_STANDARD 20 )
set( BUILD_SHARED_LIBS OFF )

# Macros the bundled libraries expect from the main CMakeLists.txt
macro( make_release_only )
	set( CMAKE_C_FLAGS_MINSIZEREL ${CMAKE_C_FLAGS_RELEASE} )
	set( CMAKE_C_FLAGS_RELWITHDEBINFO ${CMAKE_C_FLAGS_RELEASE} )
	set( CMAKE_CXX_FLAGS_MINSIZEREL ${CMAKE_CXX_FLAGS_RELEASE} )
	set( CMAKE_CXX_FLAGS_RELWITHDEBINFO ${CMAKE_CXX_FLAGS_RELEASE} )
endmacro()

add_subdirectory( "${UZDOOM_ROOT}/libraries/miniz" miniz )
add_subdirectory( "${UZDOOM_ROOT}/libraries/bzip2" bzip2 )
add_subdirectory( "${UZDOOM_ROOT}/libraries/lzma" lzma )

set( BZIP2_INCLUDE_DIR "${UZDOOM_ROOT}/libraries/bzip2" )
set( BZIP2_LIBRARIES bz2 )
set( LZMA_INCLUDE_DIR "${UZDOOM_ROOT}/libraries/lzma/C" )

set( CROSS_EXPORTS "" )
add_subdirectory( "${UZDOOM_ROOT}/tools" tools )

export( TARGETS ${CROSS_EXPORTS} FILE "${CMAKE_BINARY_DIR}/ImportExecutables.cmake" )
