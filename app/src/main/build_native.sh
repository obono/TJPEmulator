javac -h jni -d ../../build/intermediates/javac/debug/classes java/com/obnsoft/tjpemu/Native.java
ndk-build APP_ABI="armeabi-v7a arm64-v8a x86_64" APP_PLATFORM=android-28 NDK_APP_DST_DIR="jniLibs/\$(TARGET_ARCH_ABI)"
