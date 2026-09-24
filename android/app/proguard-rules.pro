# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class app.vigil.**$$serializer { *; }
-keepclassmembers class app.vigil.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }

# BouncyCastle Ed25519
-keep class org.bouncycastle.crypto.** { *; }
-keep class org.bouncycastle.math.ec.rfc8032.** { *; }
-dontwarn org.bouncycastle.**

# Mobile Wallet Adapter
-keep class com.solana.mobilewalletadapter.** { *; }
-dontwarn com.solana.**

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
