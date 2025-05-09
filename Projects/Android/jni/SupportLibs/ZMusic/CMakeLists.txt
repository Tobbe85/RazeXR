cmake_minimum_required(VERSION 3.13...3.19)
project(ZMusic
	VERSION 1.1.4
	LANGUAGES C CXX
)

list(APPEND CMAKE_MODULE_PATH ${PROJECT_SOURCE_DIR}/cmake)

include(CMakePackageConfigHelpers)
include(GNUInstallDirs)
include(ZUtility)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# If building standalone give the user the option to build shared or static.
# Otherwise the vendoring project should set the variable.
if(PROJECT_SOURCE_DIR STREQUAL CMAKE_SOURCE_DIR)
	option(BUILD_SHARED_LIBS "Build shared libraries" ON)
elseif(NOT DEFINED BUILD_SHARED_LIBS)
	set(BUILD_SHARED_LIBS ON)
endif()

if(NOT CMAKE_BUILD_TYPE)
	set(CMAKE_BUILD_TYPE Debug CACHE STRING
		"Choose the type of build, options are: Debug Release RelWithDebInfo MinSizeRel."
		FORCE)
endif()

if(MSVC AND CMAKE_VERSION VERSION_LESS 3.15)
	message(WARNING "Some things may be misconfigured. Please update to CMake >= 3.15 with Visual Studio.")
endif()
if(NOT CMAKE_MSVC_RUNTIME_LIBRARY)
	set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

if(CMAKE_INSTALL_PREFIX_INITIALIZED_TO_DEFAULT)
	set(CMAKE_INSTALL_PREFIX "/usr" CACHE PATH "Install path prefix" FORCE)
endif()

if(MSVC)
	#  optionally generate assembly output for checking crash locations.
	option(ZMUSIC_GENERATE_ASM "Generate assembly output." OFF)
	if(ZMUSIC_GENERATE_ASM)
		add_compile_options("/FAcs")
	endif()

	add_compile_options(
		"/GF" # String pooling
		"/Gy" # Function-level linking
		"/GR-" # Disable run-time type information
		"/permissive-"
		"/Oy" "/Oi" "/GS-"

		# Disable warnings for unsecure CRT functions from VC8+
		"/wd4996"
	)

	add_compile_definitions(
		"UNICODE"
		"_UNICODE"
		"_WIN32_WINNT=0x0600"
		# Debug allocations in debug builds
		$<$<CONFIG:DEBUG>:_CRTDBG_MAP_ALLOC>
	)

	add_link_options(
		"/opt:ref" # Eliminate unreferenced functions and data
		"/opt:icf" # Perform identical COMDAT folding
		"/nodefaultlib:msvcrt"
		$<$<STREQUAL:$<TARGET_PROPERTY:TYPE>,EXECUTABLE>:/TSAWARE>
		"/LARGEADDRESSAWARE"
	)

	# RelWithDebInfo uses /Ob1 by default instead of Ob2 like Release
	string(REPLACE "/Ob1 " "/Ob2 " CMAKE_CXX_FLAGS_RELWITHDEBINFO ${CMAKE_CXX_FLAGS_RELWITHDEBINFO} )

	# The CMake configurations set /GR by default, which conflict with our settings.
	# CMake 3.20 fixes the need to do this
	string(REPLACE " /GR" " " CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS} )
else()
	add_compile_options("-ffp-contract=off")

	if(APPLE)
		set(CMAKE_OSX_DEPLOYMENT_TARGET "10.9")
		if(CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
			# With standard Apple tools -stdlib=libc++ needs to be specified in order to get
			# C++11 support using SDKs 10.7 and 10.8.
			add_compile_options("-stdlib=libc++")
			add_link_options("-stdlib=libc++")
		elseif(CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
			# If we're compiling with a custom GCC on the Mac (which we know since g++-4.2 doesn't support C++11) statically link libgcc.
			add_compile_options("-static-libgcc")
		endif()
	endif()
endif()

# Initialize our list of find_package dependencies for configure_package_config_file
set(ZMUSIC_PACKAGE_DEPENDENCIES "" CACHE INTERNAL "")

add_subdirectory(thirdparty)
add_subdirectory(source)

write_basic_package_version_file(
	${CMAKE_CURRENT_BINARY_DIR}/ZMusicConfigVersion.cmake
	VERSION ${PROJECT_VERSION}
	COMPATIBILITY SameMajorVersion
)
configure_package_config_file(
	cmake/ZMusicConfig.cmake.in
	${CMAKE_CURRENT_BINARY_DIR}/ZMusicConfig.cmake
	INSTALL_DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake
)
install(FILES ${CMAKE_CURRENT_BINARY_DIR}/ZMusicConfig.cmake ${CMAKE_CURRENT_BINARY_DIR}/ZMusicConfigVersion.cmake
	DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/ZMusic
	COMPONENT devel
)

if(PROJECT_SOURCE_DIR STREQUAL CMAKE_SOURCE_DIR)
	set(CPACK_PACKAGE_CONTACT "First Last <example@example.com>" CACHE STRING "Contact info for archive maintainer.")
	set(CPACK_PACKAGE_DESCRIPTION_SUMMARY "GZDoom's music system as a standalone library")
	string(TOLOWER "${PROJECT_NAME}" CPACK_PACKAGE_NAME)
	set(CPACK_PACKAGE_NAME "lib${CPACK_PACKAGE_NAME}")
	# Use same prefix for packaging for consistency
	set(CPACK_PACKAGING_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}")
	set(CPACK_STRIP_FILES ON)
	set(CPACK_INSTALL_DEFAULT_DIRECTORY_PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE GROUP_READ GROUP_EXECUTE WORLD_READ WORLD_EXECUTE)
	set(CPACK_PACKAGE_VERSION_MAJOR ${VERSION_MAJOR})
	set(CPACK_PACKAGE_VERSION_MINOR ${VERSION_MINOR})
	set(CPACK_PACKAGE_VERSION_PATCH ${VERSION_PATCH})

	set(CPACK_COMPONENT_DEVEL_DEPENDS Full Lite)

	if(WIN32)
		set(CPACK_GENERATOR "ZIP")
	else()
		set(CPACK_GENERATOR "DEB")
		set(CPACK_DEB_COMPONENT_INSTALL ON)
		set(CPACK_DEBIAN_ENABLE_COMPONENT_DEPENDS ON)
		set(CPACK_DEBIAN_FILE_NAME "DEB-DEFAULT")
		set(CPACK_DEBIAN_PACKAGE_HOMEPAGE "https://zdoom.org")
		set(CPACK_DEBIAN_PACKAGE_SECTION "libs")
		set(CPACK_DEBIAN_PACKAGE_SHLIBDEPS ON)
		set(CPACK_DEBIAN_PACKAGE_CONTROL_STRICT_PERMISSION ON)
		set(CPACK_DEBIAN_COMPRESSION_TYPE "xz")

		set(CPACK_DEBIAN_FULL_PACKAGE_NAME "${CPACK_PACKAGE_NAME}${PROJECT_VERSION_MAJOR}")
		set(CPACK_DEBIAN_LITE_PACKAGE_NAME "${CPACK_PACKAGE_NAME}lite${PROJECT_VERSION_MAJOR}")

		set(CPACK_DEBIAN_DEVEL_PACKAGE_NAME "${CPACK_PACKAGE_NAME}-dev")
		set(CPACK_DEBIAN_DEVEL_PACKAGE_SECTION "libdevel")
	endif()

	include(CPack)
endif()
