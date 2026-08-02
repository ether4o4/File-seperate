# Keep Room generated code.
-keep class androidx.room.** { *; }

# LibreOfficeKit is loaded via JNI; keep its entry points and native methods.
-keep class org.libreoffice.kit.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Media3 / ExoPlayer
-dontwarn com.google.android.exoplayer2.**
