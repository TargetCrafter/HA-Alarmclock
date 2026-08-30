# HA Alarm Clock

An Android alarm clock app that works fully offline like the stock Clock app, and
optionally mirrors its alarms into Home Assistant over MQTT so you can build
automations around them (e.g. turn on lights when an alarm rings, or let HA
snooze/dismiss it).

## How it works

- **Alarms are 100% local.** They're stored in a Room database and scheduled with
  `AlarmManager.setAlarmClock()` — the same API the stock Clock app uses, so alarms
  fire exactly on time, survive Doze, and don't need the "schedule exact alarms"
  permission dance. If Home Assistant or MQTT is unreachable, alarms still ring.
- **Home Assistant sync is optional and additive**, via MQTT + [Home Assistant MQTT
  discovery](https://www.home-assistant.io/integrations/mqtt/#mqtt-discovery). When
  enabled in Settings, a foreground service (`HaSyncService`) keeps a connection to
  your MQTT broker (e.g. the Mosquitto add-on) open and publishes:
  - one `switch` entity per alarm (enable/disable it from HA or from the app; either
    direction updates the other)
  - a `binary_sensor` for whether an alarm is currently ringing
  - a `sensor` with the timestamp of the next alarm
  - `button` entities to snooze/dismiss the currently ringing alarm from HA
  - all grouped under one HA device, with a retained availability/LWT topic so HA
    correctly shows the device offline if the app is killed or loses connectivity

## Project layout

```
app/src/main/java/com/targetcrafter/haalarmclock/
  data/     Room entity/DAO + AlarmRepository (source of truth for alarms)
  alarm/    AlarmManager scheduling, the BroadcastReceiver that fires alarms,
            the ringing foreground service + full-screen UI, boot rescheduling
  mqtt/     MQTT settings storage, HA discovery payload/topic builders,
            MqttManager (connection + publish/subscribe), the sync foreground service
  ui/       Jetpack Compose screens (alarm list, editor, settings) + ViewModels
```

## Building

Requires JDK 17+, the Android SDK (compileSdk 35), and network access to
`dl.google.com`/Maven Central for AndroidX, Compose, Room, and the HiveMQ MQTT
client dependency. This project was scaffolded in a sandboxed environment without
Android SDK/network access to Google's Maven repo, so the build has **not** been
verified end-to-end with `./gradlew assembleDebug` — please do that first thing
in a normal dev environment or CI (e.g. via `android-actions/setup-android` in
GitHub Actions) and fix up anything that doesn't compile.

```
./gradlew assembleDebug
```

## Permissions

- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — required to use `setAlarmClock()`.
- `POST_NOTIFICATIONS` — for the ringing alarm and the (minimum-priority) MQTT sync
  status notification.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — the ringing service plays the alarm sound.
- `FOREGROUND_SERVICE_DATA_SYNC` — the MQTT sync service keeps a network connection.
- `RECEIVE_BOOT_COMPLETED` — alarms and the MQTT connection are re-armed after reboot.

## Not yet implemented

Timers, stopwatch, and world clock (this first pass is alarms-only, matching the
stock Clock app's alarm tab). The MQTT broker password is stored in
`EncryptedSharedPreferences`.
