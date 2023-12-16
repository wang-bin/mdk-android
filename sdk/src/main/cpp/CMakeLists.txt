# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.10.2)

# Declares and names the project.

project("mdkplayer")

set(MDK_SDK_URL "https://sourceforge.net/projects/mdk-sdk/files/nightly/mdk-sdk-android.7z")
set(MDK_SDK_SAVE ${CMAKE_CURRENT_SOURCE_DIR}/mdk-sdk-android.7z)
if(NOT EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/mdk-sdk/lib/cmake/FindMDK.cmake)
  if(NOT EXISTS ${MDK_SDK_SAVE})
    message("Downloading mdk-sdk from ${MDK_SDK_URL}")
    file(DOWNLOAD ${MDK_SDK_URL} ${MDK_SDK_SAVE} SHOW_PROGRESS)
  endif()
  execute_process(
    COMMAND ${CMAKE_COMMAND} -E tar "xvf" ${MDK_SDK_SAVE} "--format=7zip"
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    OUTPUT_STRIP_TRAILING_WHITESPACE
    RESULT_VARIABLE EXTRACT_RET
  )
  if(NOT EXTRACT_RET EQUAL 0)
    file(REMOVE ${MDK_SDK_SAVE})
    message(FATAL_ERROR "Failed to extract mdk-sdk. You can download manually from ${MDK_SDK_URL} and extract to ${CMAKE_CURRENT_SOURCE_DIR}")
  endif()
endif()
include(${CMAKE_CURRENT_SOURCE_DIR}/mdk-sdk/lib/cmake/FindMDK.cmake)

set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)
# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             mdk-jni

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
        jmi/jmi.cpp
        MDKPlayerJNI.cpp)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       mdk-jni
                       PRIVATE
                       mdk
                       # Links the target library to the log library
                       # included in the NDK.
                       -llog -landroid)
