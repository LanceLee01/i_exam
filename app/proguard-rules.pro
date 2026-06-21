-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep data classes used for JSON deserialization
-keep class com.examhelper.app.network.** { *; }

# Apache POI / Log4j2 / OSGi / XMLBeans / Saxon
-dontwarn aQute.bnd.annotation.**
-dontwarn org.osgi.**
-dontwarn org.apache.logging.log4j.util.OsgiServiceLocator
-dontwarn org.apache.poi.**
-dontwarn javax.xml.stream.**
-dontwarn net.sf.saxon.**
-dontwarn com.gemalto.jp2.**
-dontwarn java.awt.**
-dontwarn org.openxmlformats.schemas.**
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class net.sf.saxon.** { *; }
-keep class javax.xml.stream.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
