# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Room with R8
-keep class * extends androidx.room.RoomDatabase
-keep class * implements androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class * { @androidx.room.Dao *; }
-keep class * { @androidx.room.Database *; }

# Keep all models/entities in local package
-keep class com.focux.pulse.data.local.entities.** { *; }

# WorkManager
-keep class * extends androidx.work.ListenableWorker

# Lifecycle
-keep class * extends androidx.lifecycle.ViewModel

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Preserve line numbers for debugging crash logs
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile