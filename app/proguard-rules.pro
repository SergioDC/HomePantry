# Firestore (toObject/set) y Gson (Retrofit) rellenan estas clases por reflexión
# usando los nombres de sus campos; si R8 los renombra o los quita, los datos
# dejan de leerse o de guardarse sin dar error de compilación.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.homepantry.app.data.** { *; }
