# ==================== AWS SDK ====================
# 仅保留实际使用的AWS类
-keep class software.amazon.awssdk.services.s3.** { *; }
-keep class software.amazon.awssdk.auth.credentials.** { *; }
-keep class software.amazon.awssdk.regions.** { *; }
-keep class software.amazon.awssdk.http.** { *; }
-dontwarn software.amazon.awssdk.**

# ==================== Baidu Paddle Lite ====================
# 保留Paddle Lite核心类
-keep class com.baidu.paddle.lite.MobileConfig { *; }
-keep class com.baidu.paddle.lite.PaddlePredictor { *; }
-keep class com.baidu.paddle.lite.PowerMode { *; }
-keep class com.baidu.paddle.lite.ConfigBase { *; }
-dontwarn com.baidu.paddle.**

# 移除未使用的日志功能
-assumenosideeffects class com.baidu.paddle.lite.** {
    public static void enableLiteLog(...);
}

# ==================== Room数据库 ====================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
}
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ==================== 数据模型 ====================
-keep class com.example.inventory.data.model.** { *; }

# ==================== Moshi JSON ====================
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonQualifier interface *

# ==================== OkHttp/Retrofit ====================
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes Exceptions

# ==================== Apache POI ====================
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**

# Apache POI 可选依赖 - 忽略缺失的类
-dontwarn org.apache.logging.log4j.**
-dontwarn aQute.bnd.annotation.spi.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn javax.xml.stream.**
-dontwarn net.sf.saxon.**
-dontwarn org.osgi.framework.**
-dontwarn java.awt.**
-dontwarn com.graphbuilder.**

# ==================== Jackcess ====================
-keep class com.healthmarketscience.jackcess.** { *; }
-dontwarn com.healthmarketscience.jackcess.**

# ==================== Kotlin ====================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ==================== 协程 ====================
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ==================== 加密相关 ====================
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# ==================== 通用配置 ====================
# 保留注解
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# 保留泛型
-keepattributes Signature

# 保留崩溃堆栈信息
-keepattributes EnclosingMethod

# 保留序列化相关
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
