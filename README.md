# MyLife AI · Automation Studio

Kotlin development build for Android 10 and later. Open Automation studio to choose a trigger, add conditions and assemble actions. Save the rule, then use Run now or let the configured event trigger it. The rule list supports search, editing, duplication, disabling and deletion.

The dashboard includes a Quran player, Bangla/English voice preview, local memory, contacts, execution history and permission settings. Scheduled browser and audio actions post a notification. Tap it to open the destination. The native player supports long-running playback with repeat, progress, speed and notification controls. Al-Baqarah is available on demand. Local recordings are selected through the Android document picker.

The development report covers permission requirements, trigger delivery limits, external actions, backups, audio attribution and Huawei testing. [Engineering report](DEVELOPMENT.md)

## Build and install the APK

1. Open [Build MyLife AI APK](https://github.com/csgayeasinarafat-cmd/apk/actions/workflows/android.yml) in GitHub Actions.
2. To build again, choose **Run workflow** on the `development/automation-studio` branch.
3. Open the successful run and download **MyLifeAI-debug-APK** from **Artifacts**.
4. Unzip the download, transfer `app-debug.apk` to the phone, and open it to install.

GitHub Actions builds the debug APK with Java 17, Gradle 8.7, and Android SDK 35. The workflow verifies the APK signature and launches the app on an Android 10 emulator. Test scheduling, reboot, notifications, and audio on the Huawei Y8p to verify behavior under EMUI.

For a local build with the same tools, run `gradle assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
