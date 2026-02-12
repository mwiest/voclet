# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Hide original source file name
-renamesourcefileattribute SourceFile

# ============================================
# Room Database
# ============================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ============================================
# Hilt / Dagger
# ============================================
-dontwarn dagger.hilt.internal.aggregatedroot.codegen.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

# ============================================
# Jetpack Compose
# ============================================
-dontwarn androidx.compose.**

# ============================================
# Kotlin Serialization
# ============================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.github.mwiest.voclet.**$$serializer { *; }
-keepclassmembers class com.github.mwiest.voclet.** {
    *** Companion;
}
-keepclasseswithmembers class com.github.mwiest.voclet.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============================================
# Firebase
# ============================================
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ============================================
# CameraX
# ============================================
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ============================================
# Kotlin Coroutines
# ============================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============================================
# Apache Commons CSV
# ============================================
-dontwarn org.apache.commons.csv.**
-keep class org.apache.commons.csv.** { *; }

# ============================================
# OkHttp / OkIO (used by Firebase)
# ============================================
-dontwarn okhttp3.**
-dontwarn okio.**
