# Add project specific ProGuard rules here.

# Chromecast: el Cast SDK instancia el OptionsProvider por reflexión (meta-data del manifest)
-keep class com.applemusic.android.playback.cast.LiramuCastOptionsProvider { *; }
