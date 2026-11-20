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

## Build and install the APK

1. Open [Build MyLife AI APK](https://github.com/csgayeasinarafat-cmd/apk/actions/workflows/android.yml) in GitHub Actions.
2. To build again, choose **Run workflow** on the `main` branch. A push to `main` also starts a build.
3. Open the successful run and download **MyLifeAI-debug-APK** from **Artifacts**.
4. Unzip the download, transfer `app-debug.apk` to the phone, and open it to install.

GitHub Actions builds the debug APK with Java 17, Gradle 8.7, and Android SDK 35. The workflow verifies the APK signature and launches the app on an Android 10 emulator. Test scheduling, reboot, notifications, and audio on the Huawei Y8p to verify behavior under EMUI.

For a local build with the same tools, run `gradle assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
