cmake_minimum_required(VERSION 3.22.1)

project("idverify-native")

# Configure OpenCV (Absolute path for reliability during dev)
set(OpenCV_DIR /Users/yek/AndroidStudioProjects/idverify/idverify-sdk/android/libs/opencv/sdk/native/jni)
find_package(OpenCV REQUIRED)

add_library(
        idverify-native
        SHARED
        native-lib.cpp
        VisionProcessor.cpp)

find_library(
        log-lib
        log)
        
find_library(
        bitmap-lib
        jnigraphics)

target_link_libraries(
        idverify-native
        ${OpenCV_LIBS}
        ${log-lib}
        ${bitmap-lib})
