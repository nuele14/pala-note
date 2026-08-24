# Proguard rules for ES1 Companion
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
