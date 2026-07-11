# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Moshi
-keep class com.seenslide.teacher.core.network.model.** { *; }
-keepclassmembers class com.seenslide.teacher.core.network.model.** { *; }
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier @interface *
-keepclassmembers @com.squareup.moshi.JsonClass class * extends java.lang.Enum {
    <fields>;
    **[] values();
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * { @androidx.room.* <fields>; }

# Room Database entities
-keep class com.seenslide.teacher.core.database.** { *; }

# Coil
-dontwarn coil.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Kotlin serialization / reflection
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# ZXing QR
-keep class com.google.zxing.** { *; }

# Keep BuildConfig
-keep class com.seenslide.teacher.BuildConfig { *; }
