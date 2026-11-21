# Own code is no longer blanket-kept so R8 can shrink it. Only what reflection needs:
-dontwarn org.slf4j.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# kotlinx.serialization (ThemeSettings, NamedPreset)
-keepclassmembers class com.prism.gallery.** { *** Companion; }
-keepclasseswithmembers class com.prism.gallery.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.prism.gallery.**$$serializer { *; }
