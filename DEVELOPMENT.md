# MyLife AI engineering report

## Baseline audit

Inspected Git tree acd7783, including all six Kotlin files, manifest, project settings and Actions workflow. The existing successful API 29 launch check establishes installation and dashboard startup. It does not exercise a scheduled alarm or audio.

| Component | Existing behavior | Gap or risk |
|---|---|---|
| MainActivity | Local contact, speech activity, typed keywords, system dialer, WhatsApp and Wi-Fi settings | Keyword matching can misinterpret a sentence or a contact. Speech service availability depends on the handset. |
| AutomationEngine | Legacy daily rules in preference JSON. Receiver looks up current rule before acting | Malformed JSON can make the list appear empty. No run history. |
| AdvancedActivity / AdvancedRules | Local weekday/time rules, multiple per-action notifications, edit, enable and delete | Raw action syntax, no independent conditions, multiple notifications are not a sequential executor. Embedded demo audio had no documented usage rights. |
| RuleReceiver | Separate 23:00 reminder. Recalculates after time change or reboot | Shares scheduling responsibility with two other engines. Delivery under Huawei power management needs a physical test. |
| AudioPlayer | User-started MediaPlayer foreground service with Stop | Missing audio focus, pause/resume, repeat, progress and source selection. Prepare listener installed after prepareAsync. |
| Manifest | Only launcher activity exported to other apps. Local preferences are sandboxed, backup and cleartext disabled | Notification permission requested during startup. Denied notifications can hide the outcome of a rule. |
| Build | AGP 8.6.1, Kotlin 2.0.21, SDK 35, minimum 29, target 34, JDK 17, hosted Gradle 8.7 | No wrapper. Baseline workflow builds and verifies signature but tests only startup. Debug signing is runner-local. |

## Reference comparison

The comparison covers publicly documented behavior. Product storage engines, service implementations and error recovery are unknown unless identified below. No proprietary application code was obtained.

| Product and evidence | User interaction and execution model | Equivalent for MyLife AI and prerequisites |
|---|---|---|
| [Tasker run log](https://tasker.joaoapps.com/userguide/en/activity_runlog.html) | Profiles activate tasks. The optional run log links profile transitions, task execution and actions. | Record trigger, result and reason locally. Distinguish posting a notification from completion in an external app. |
| [MacroDroid wiki](https://macrodroidforum.com/wiki/index.php/Trigger%3A_System_log) | Macros use triggers, actions and constraints. A log trigger can match text, including patterns. | Present three editable sections. Internal events need no device-wide log access. Guard against a rule triggering itself. |
| [IFTTT filters](https://help.ifttt.com/hc/en-us/articles/360052451954-What-is-a-Filter) | Applets evaluate trigger/query data before delays and actions. Filters can skip individual actions. Connected services handle external operations. | Evaluate typed predicates against one device snapshot. An unavailable sensor should produce an unknown result. Avoid shipping cloud service credentials. |
| [Google Assistant routines](https://support.google.com/assistant/answer/7672035) | A routine has starters and actions. Voice, time, presence or a manual Play control can start a compatible routine. Equipment and account configuration affect availability. | Convert typed or recognized speech to an editable preview. Use the installed recognizer if available and keep typed input available on EMUI. |
| [Musicolet privacy guide](https://krosbits.in/musicolet/docs/privacy_policy/musicolet_policy.htm) | Queues, playlists, favorite tracks and positions stay in private device storage. The player centers on the user's library. | Select recordings with Android's document picker. Persist URI access and media position. Keep playback ownership in a foreground service. |
| [TickTick voice add](https://help.ticktick.com/articles/7082552170989486080) | Mobile voice capture extracts task details including dates, lists and priority. | Parse supported Bangla and English expressions locally. Ask about missing time, message or contact before saving. |

## Architecture and delivery roadmap

1. Retain existing legacy rule IDs and preference stores. On first use, migrate advanced rule JSON into SQLite transactionally. Keep dated outcomes in bounded local history. Keep older screens usable during migration.
2. Extend the model with versioned trigger, condition and ordered action records. Separate event observation, predicate evaluation and delivery. Persist the next action and due time before a delay. Use a stable execution ID and a maximum nesting depth for rules calling rules.
3. Add explicit permission onboarding per trigger. Bluetooth, location, app usage and notification access each need their own opt-in and revocation behavior. Show observation gaps if Android suspends monitoring. Avoid Accessibility for app-open detection.
4. Keep local contact, template and reciter records independently editable. Export through the system file picker. Validate a complete imported backup before committing it in one transaction.
5. Own native playback in a media service with audio focus, media controls and durable URI grants. Verify rights for each distributed recording. Browser playback remains a notification tap and site Play control.
6. Add rule-engine tests for midnight boundaries, one-shot dates, invalid input, migration and notification denial. Test process death during a delayed sequence and unplugged physical playback.

## Huawei Y8p acceptance and installation

1. Open the successful Actions run for the development commit and download MyLifeAI-debug-APK under Artifacts. Extract app-debug.apk and open it on the phone. Use the install permission for the file-opening app if prompted.
2. If an existing installation has a different signing certificate, Android can refuse an update. Preserve the user's existing data before considering a fresh installation.
3. Save a reminder for two minutes ahead with a unique message. Lock the screen, record the expected and actual time, and inspect the notification and execution history.
4. Restart the app, edit and disable the rule, then duplicate and delete the copy. Reboot the handset and verify the enabled rule reschedules. Repeat with EMUI background launch restricted and allowed.
5. Check the browser chooser, dialer, WhatsApp confirmation, unavailable speech service and denied notifications. On newer Android versions repeat with exact-alarm access revoked. Check native audio with a permitted local recording, unplugged headphones, interruption, screen lock and Stop.

Physical Y8p acceptance requires a real device. The repository's emulator uses Android 10 without EMUI.

