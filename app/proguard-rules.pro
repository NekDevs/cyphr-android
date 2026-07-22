-keep class org.cyphr.app.crypto.** { *; }

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.serialization.**

-keep class * extends androidx.navigation.NavArgs { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel { *; }