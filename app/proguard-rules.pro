# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the proguard-android-optimize.txt file included with the Android SDK.

# Keep MediaPipe classes
-keep class com.google.mediapipe.** { *; }

# Keep Room entities
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }

# Keep data classes used by Room
-keepclassmembers class * {
    @androidx.room.ColumnInfo <fields>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Kotlin serialization / reflection
-dontwarn kotlin.reflect.jvm.internal.**
