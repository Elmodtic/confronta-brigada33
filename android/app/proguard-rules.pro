# Reglas ProGuard (release). Retrofit/Gson usan reflexión sobre los modelos.
-keep class com.brigada.confronta.data.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
