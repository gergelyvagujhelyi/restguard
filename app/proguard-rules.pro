# ─── RestGuard ProGuard Rules ─────────────────────────────

# ─── Room ─────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# ─── Retrofit + OkHttp ───────────────────────────────────
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation interface retrofit2.Response
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class retrofit2.** { *; }

# ─── kotlinx.serialization ──────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class **$$serializer { *; }

# Keep serializable DTOs
-keep class com.restguard.data.remote.** { *; }
-keep class com.restguard.domain.service.DataExport { *; }
-keep class com.restguard.domain.service.Exported** { *; }

# ─── Hilt / Dagger ──────────────────────────────────────
-dontwarn dagger.**
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ─── Health Connect ─────────────────────────────────────
-keep class androidx.health.connect.** { *; }

# ─── Enum safety ────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─── Domain models (used via reflection by Room) ────────
-keep class com.restguard.domain.model.** { *; }
-keep class com.restguard.data.local.entity.** { *; }
