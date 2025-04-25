# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontwarn org.eclipse.jetty.**
-dontwarn com.google.firebase.messaging.FirebaseMessaging
-dontwarn javax.servlet.**
-dontwarn org.slf4j.**

# for sqlcipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.* { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.InputMerger
-keep public class * extends androidx.work.ListenableWorker {
  public <init>(...);
}
-keep class androidx.work.WorkerParameters

# For MQTT
-keep class org.eclipse.paho.client.mqttv3.** {*;}
-keep class org.eclipse.paho.android.service.** { *; }
-keepclasseswithmembers class org.eclipse.paho.** {*;}