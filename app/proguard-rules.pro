# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep WhisperLib
-keep class com.whisper.mobile.WhisperLib { *; }
