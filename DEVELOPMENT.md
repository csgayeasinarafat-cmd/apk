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


## Development implementation

The dashboard opens the visual builder, playback, voice, private memory, contacts, execution history and permission settings. The legacy dashboard still opens separately. The appearance setting selects an application theme. The dashboard has an English/Bangla switch. Some secondary screens use bilingual labels and English explanations.

Advanced rules now have a trigger, an optional target/date, a recursive condition, a description, and ordered actions. Rules are stored in private SQLite. A preference migration keeps the old data as a recovery source. Choose an action from the picker, enter its parameter, then save. Enter an app package for app-open or notification events, a paired device name for Bluetooth, and latitude, longitude and radius in metres for proximity. Permission and provider availability affect delivery. Charging, battery and connectivity observations run while the application process is alive. App usage checks are periodic and can be delayed. Notification events match the package and discard the content.

Actions dispatch in order. A user-facing action posts a notification and continues dispatching. History records posting rather than the user's completion of an external action. Delay persists a continuation; changing the rule invalidates it. Linked rules have cycle and depth checks. An interrupted delivery between claiming a continuation and posting its next action can lose that step. Full transactional delivery and semantic voice disambiguation remain future work.

For daily native Quran, select the Quran player action in a daily rule. Tap the notification at the scheduled time and press Play. Repeat is on by default. The built-in Al-Baqarah recording is Aaqib Azeez's CC0 recitation, streamed on demand. The original is about 332 MB. Additional reciters and surahs can be imported using the Android document picker. [Recording and licence](https://commons.wikimedia.org/wiki/File:Chapter_2,_Al-Baqarah_(Murattal)_-_Recitation_of_the_Holy_Qur%27an.mp3)

Memory backups include named memory entries and advanced rules. Audio files, provider access grants, legacy rules and run history are device-specific. Choose a private location for the readable JSON file. Imported rules are disabled for review. Previously saved legacy trusted numbers remain available in legacy tools. Name contacts in Personal Memory for voice matching.

The optional usage service reads local foreground activity transitions for enabled package filters. The optional notification listener compares package names only. The location service relies on Android proximity alerts and available device providers. Revocation, Bluetooth delivery, standby and EMUI battery management require physical tests. Time and weekday rules require no Google services.

## Public implementation references

The public Tasker plug-in SDK separates typed action input/output from the runner that receives an Android intent. This documents the plug-in boundary, not Tasker's private engine. Google's public App Actions examples map named voice capabilities into activities. MyLife uses a local rule proposal and explicit review so scheduling works independently of either SDK. [Tasker plug-in runner](https://github.com/joaomgcd/TaskerPluginSample/blob/master/taskerpluginlibrary/src/main/java/com/joaomgcd/taskerpluginlibrary/action/TaskerPluginRunnerAction.kt) · [Google App Actions examples](https://developers.google.com/assistant/app/samples)

The player uses the platform MediaPlayer, MediaSession and audio-focus APIs in a foreground service. Mobile music libraries and local playlists informed the recording picker. No source from a proprietary player is included. [Android media service guidance](https://developer.android.com/media/media3/session/background-playback) · [Musicolet playback guide](https://krosbits.in/musicolet/)

Tasker documents time, day, state, application and location contexts. MacroDroid documents initialization events for restoring configuration. IFTTT exposes service failures in the activity feed. TickTick combines task capture with calendar and NLP input. These interfaces informed the rule editor, boot receiver, outcome history and proposed-time preview. [Tasker](https://tasker.joaoapps.com/userguide/en/) · [MacroDroid](https://macrodroidforum.com/wiki/index.php/Trigger%3A_MacroDroid_Initialised) · [IFTTT](https://help.ifttt.com/hc/en-us/articles/115010194547-Common-errors-and-troubleshooting-tips) · [TickTick](https://ticktick.com/features)

### Requirements and Android limits by reference

| Pattern | Required by an Android equivalent | Background, voice and data implications |
|---|---|---|
| Tasker profile → task | Event adapter plus ordered action registry and trace IDs | Observe supported system events. Ask for special access for usage or notifications. Local typed variables can supply conditions. Public SDK code demonstrates plug-in integration; private storage and voice internals were not verified. |
| MacroDroid trigger + constraints | UI controls for each argument, predicate evaluation, enable state | Device broadcasts have different delivery rules after process death. Keep the original rule and the reason it was skipped. The reference wiki documents variables and initialization events. Voice implementation was not verified. |
| IFTTT Applet | Authenticated service for each external connector and an activity feed | Cloud events need connectivity and consent. Device actions can still require a user tap. MyLife's equivalent keeps rules on the device. Filter and service reconnection behavior are documented. |
| Assistant Routine | An available recognition service and a mapping from utterance to starter/action | Device/account availability affects starters. On a Huawei phone, allow typed commands, show extracted time and number, then confirm. Internal model and storage details were not verified. |
| Musicolet track/queue | A readable media URI, audio focus, playback service, media controls | Local library permissions differ from streaming. Lock-screen controls and saved positions are documented. No general-purpose automation trigger or voice engine was established by the cited guides. |
| TickTick capture → reminder | Parser, editable due time, task list, recurring scheduler | Notification permission, clock/time-zone changes and battery policy affect delivery. Calendar integration introduces separate account permissions. Internal error logs and scheduler implementation were not verified. |

### Test boundaries

The cloud workflow builds the same debug variant it installs, runs JVM tests for scheduling and time parsing, checks its signature and launches the real activity on API 29. Emulator QA uses disposable data to exercise the rule editor, local store, event delivery and player. A test tone checks audio mechanics; listening quality and the recording stream need separate verification. Cloud signing uses ephemeral debug keys. A stable user-controlled signing key is future release work.
