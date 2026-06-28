# Add project specific ProGuard rules here.

# Keep line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── Firebase Firestore ───────────────────────────────────────────────────────
# Keep all model classes used for Firestore deserialization
-keep class com.appname.dqra.model.** { *; }
-keepclassmembers class com.appname.dqra.model.** { *; }

# Firebase SDK internals
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ─── OkHttp ──────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ─── OSMDroid (Map) ──────────────────────────────────────────────────────────
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ─── Repository and ViewModel layers ─────────────────────────────────────────
-keep class com.appname.dqra.repository.** { *; }
-keep class com.appname.dqra.viewmodel.** { *; }
-keep class com.appname.dqra.listeners.** { *; }
-keep class com.appname.dqra.cache.** { *; }

# ─── AndroidX / Lifecycle ────────────────────────────────────────────────────
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# ─── Prevent stripping of Parcelable implementations ─────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ─── Enums ───────────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─── Notification Listener Service ───────────────────────────────────────────
-keep class com.appname.dqra.listeners.MessengerNotificationListener { *; }
-keep class com.appname.dqra.listeners.AlertService { *; }
-keep class com.appname.dqra.listeners.FirestoreService { *; }
