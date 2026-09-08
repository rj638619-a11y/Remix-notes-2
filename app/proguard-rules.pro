# Add project specific ProGuard rules here.

# --- Base rules ---

-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-keep class kotlin.Metadata { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider

# --- Kotlin Coroutines ---

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}

# --- Jetpack Compose ---

-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# --- Material Components ---

-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# --- OkHttp ---

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Room ---

-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# --- Biometric ---

-keep class androidx.biometric.** { *; }

# --- WorkManager ---

-keep class androidx.work.** { *; }

# --- ML Kit & CameraX ---

-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# --- App-specific data models & databases ---

-keep class com.example.data.model.** { *; }
-keep class com.example.**$Companion { *; }
-keepclassmembers class com.example.data.model.** { *; }

-keep class com.example.data.db.AppDatabase { *; }
-keep class com.example.data.db.NoteDao { *; }
-keep class com.example.data.sync.HtmlSyncWorker { *; }
-keep class com.example.util.DetectedFaceData { *; }
-keep class com.example.util.FaceSignature { *; }
