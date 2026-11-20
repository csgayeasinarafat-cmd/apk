# MyLife AI v1.2 — Huawei Y8p / Android 10+

## Features
- Local advanced daily rule editor: IF time AND weekdays (1=Monday ... 7=Sunday), THEN multiple actions.
- Supported actions: `WEB|https://quran.com/2`, `AUDIO|https://.../002.mp3`, `WIFI|settings`, `DIAL|+880...`, `WHATSAPP|880...`, `NOTE|text`.
- Multiple action lines generate separate notification entry points; user taps to proceed.
- Browser opens Quran.com, but cannot guarantee Chrome, browser autoplay, or endless repeat.
- In-app audio foreground service with user-initiated Play and Stop notification. Audio stops at track completion.
- Demo Morning Quran rule is **disabled by default**. It includes a sample third-party direct MP3 URL that has NOT been checked for availability, rights or accuracy; replace it if necessary.
- Legacy v1.1 editor remains accessible; prefer Advanced Builder for new rules.

## Android constraints
- Background activity launch is not automatic: scheduled alarm posts notification, and user taps to start playback.
- Wi-Fi settings cannot be silently toggled by normal apps on Android 10+.
- WhatsApp message and phone call require user confirmation.
- Android 12+ exact alarms may require special access; Android 13+ notifications need runtime permission.
- Huawei EMUI battery optimization can delay alarms; configure app launch/background activity and battery permissions.
- Speech recognition depends on installed speech service and may not work without Google services.
- Day/time logic uses phone local timezone.
- AND/OR support is **limited**: time AND allowed weekdays; weekdays are OR; actions are multiple parallel notifications. Arbitrary nested Boolean conditions and event triggers are not implemented.

## Build
1. Install Android Studio, JDK 17, Android SDK platform 35.
2. Open this folder, allow Gradle sync, build debug APK.
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)`; output `app/build/outputs/apk/debug/app-debug.apk`.
4. Test on physical Huawei Y8p, especially scheduling, reboot, notifications and audio.

## Status
Source project only. No Android SDK or Gradle executable was available in the creation environment, so compilation, APK generation and device testing were NOT performed.
