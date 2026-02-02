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

# ==================== Apache POI Rules ====================
# Keep only essential XWPF (DOCX) and XSSF (XLSX) classes

# Keep DOCX parsing classes
-keep class org.apache.poi.xwpf.** { *; }
-keep class org.apache.poi.xwpf.usermodel.** { *; }

# Keep XLSX parsing classes
-keep class org.apache.poi.xssf.** { *; }
-keep class org.apache.poi.xssf.usermodel.** { *; }

# Keep common POI classes needed by both
-keep class org.apache.poi.ss.usermodel.** { *; }
-keep class org.apache.poi.ss.util.** { *; }
-keep class org.apache.poi.wp.usermodel.** { *; }
-keep class org.apache.poi.common.usermodel.** { *; }
-keep class org.apache.poi.util.** { *; }
-keep class org.apache.poi.openxml4j.** { *; }
-keep class org.apache.poi.ooxml.** { *; }
-keep class org.apache.poi.schemas.** { *; }

# Keep XMLBeans (required for OOXML parsing)
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }

# Dontwarn for optional/unused POI features
-dontwarn org.apache.poi.hslf.**
-dontwarn org.apache.poi.hwpf.**
-dontwarn org.apache.poi.hssf.**
-dontwarn org.apache.poi.hdgf.**
-dontwarn org.apache.poi.hsmf.**
-dontwarn org.apache.poi.hpbf.**
-dontwarn org.apache.poi.hemf.**
-dontwarn org.apache.poi.hwmf.**
-dontwarn org.apache.poi.sl.**
-dontwarn org.apache.poi.ddf.**
-dontwarn org.apache.poi.extractor.**
-dontwarn org.apache.poi.poifs.**

# Don't warn about optional commons dependencies
-dontwarn org.apache.commons.codec.**
-dontwarn org.apache.commons.collections4.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.math3.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.logging.**
-dontwarn org.apache.log4j.**

# Don't warn about optional graphics/drawing
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

# ==================== Kotlin/Compose Rules ====================
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Keep Compose-related classes
-keep class androidx.compose.** { *; }

# Keep ViewModel classes
-keep class * extends androidx.lifecycle.ViewModel { *; }
