# Testmond release (R8) keep rules.
# R8 already ships correct rules for Compose, AndroidX and kotlinx.serialization via their
# consumer rules; the rules below are an extra safety net for the parts of this app that are
# reached by name rather than by direct code reference.

# --- WebView <-> Kotlin bridge (KaTeX height reporting) ---------------------------------------
# JavaScript calls TestmondBridge.onHeight(...) by name, so neither the method nor its
# containing class may be renamed or removed.
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.testmond.app.ui.HeightBridge { *; }

# --- .mcqz / settings JSON (kotlinx.serialization) --------------------------------------------
# Keeps the serialized model classes, their generated serializers and field names intact so
# files and settings written by older builds keep loading.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-keep,includedescriptorclasses class com.testmond.app.model.**$$serializer { *; }
-keepclassmembers class com.testmond.app.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.testmond.app.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers enum com.testmond.app.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Crash reports keep readable line numbers --------------------------------------------------
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
