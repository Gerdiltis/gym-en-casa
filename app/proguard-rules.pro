# kotlinx.serialization: keep generated serializers for @Serializable classes
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers @kotlinx.serialization.Serializable class com.gymencasa.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.gymencasa.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
