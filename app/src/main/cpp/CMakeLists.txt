cmake_minimum_required(VERSION 3.22.1)

set(OpenCV_STATIC on)
set(OpenCV_DIR C:/Users/Antek/Desktop/Studia/PBLA/tools/OpenCV-android-sdk/sdk/native/jni)
set(ANDROID_NDK_ABI_NAME x86_64)
find_package(OpenCV REQUIRED)

project("driverappprototype")


add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp
        opencv-utils.cpp)

find_library(jnigraphics-lib jnigraphics)

target_link_libraries(${CMAKE_PROJECT_NAME}
        ${OpenCV_LIBS}
        ${jnigraphics-lib}

        android
        log)