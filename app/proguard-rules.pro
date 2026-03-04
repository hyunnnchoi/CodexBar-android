# ===== kotlinx.serialization =====
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.yourname.aiquota.**$$serializer { *; }
-keepclassmembers class com.yourname.aiquota.** {
    *** Companion;
}
-keepclasseswithmembers class com.yourname.aiquota.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable classes
-keep @kotlinx.serialization.Serializable class * { *; }

# ===== Retrofit + OkHttp =====
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Keep Retrofit service interfaces
-keep,allowobfuscation interface com.yourname.aiquota.core.network.** {
    <methods>;
}

# ===== Hilt =====
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ===== WorkManager =====
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ===== RemoteViews (Notification) =====
-keep class android.widget.RemoteViews { *; }

# ===== General Android =====
-keep class * extends android.service.quicksettings.TileService { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
