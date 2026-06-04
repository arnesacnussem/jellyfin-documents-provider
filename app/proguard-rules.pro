-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

-keep,includedescriptorclasses class arne.jellyfindocumentsprovider.**$$serializer { *; }
-keepclassmembers class arne.jellyfindocumentsprovider.** { *** Companion; }
-keepclasseswithmembers class arne.jellyfindocumentsprovider.** { kotlinx.serialization.KSerializer serializer(...); }

-keep,includedescriptorclasses class org.jellyfin.sdk.model.**$$serializer { *; }
-keepclassmembers class org.jellyfin.sdk.model.** { *** Companion; }
-keepclasseswithmembers class org.jellyfin.sdk.model.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class org.jellyfin.sdk.model.** { *; }

-keep class io.ktor.** { *; }

-dontwarn okhttp3.**
-dontwarn okio.**

-keep class com.maxmpz.** { *; }

-keep class arne.jellyfindocumentsprovider.vfs.AlbumInfo { *; }
-keep class arne.jellyfindocumentsprovider.vfs.VirtualFile { *; }
-keep class arne.jellyfindocumentsprovider.vfs.JellyfinServer { *; }
-keep class arne.jellyfindocumentsprovider.vfs.ThumbCache { *; }
-keep class arne.jellyfindocumentsprovider.vfs.CacheInfo { *; }
-keep class arne.jellyfindocumentsprovider.vfs.CacheChunksConverter { *; }
-keep class io.objectbox.** { *; }
