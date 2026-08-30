# HA Alarm Clock

An Android alarm clock app that works fully offline like the stock Clock app, and
optionally connects directly to Home Assistant — via a custom integration, no MQTT
broker required — so you can build automations around your alarms (e.g. turn on
lights when an alarm rings, or let HA snooze/dismiss it).

## How it works

- **Alarms are 100% local.** They're stored in a Room database and scheduled with
  `AlarmManager.setAlarmClock()` — the same API the stock Clock app uses, so alarms
  fire exactly on time, survive Doze, and don't need the "schedule exact alarms"
  permission dance. If Home Assistant is unreachable, alarms still ring.
- **Home Assistant sync is optional and additive**, talking directly to a `HA Alarm
  Clock` custom integration (see `custom_components/ha_alarmclock/`) using a normal
  Home Assistant **long-lived access token** — the same credential type used by any
  other HA API client, generated once from your HA user profile. No MQTT broker, no
  separate webhook secret.
  - **Phone → HA (state):** a foreground service (`HaSyncService`) POSTs alarm/ringing
    state to the integration's `/api/ha_alarmclock/sync` endpoint whenever it changes.
    The integration turns this into entities — one `switch` per alarm, a
    `binary_sensor` for whether an alarm is ringing, a `sensor` for the next alarm's
    timestamp — all grouped under one HA device per phone.
  - **HA → phone (commands):** the same service also opens Home Assistant's built-in
    WebSocket API (`/api/websocket`) and subscribes to a custom `ha_alarmclock_command`
    event, which the integration fires when you flip a switch or press one of the
    `Snooze`/`Dismiss` button entities it creates. The connection reconnects with
    backoff if it drops.

## UI/UX notes

- **Editing is in-place, not a separate screen.** Tap an alarm's time for a quick
  time-only popup; tap its label/repeat area for the full options sheet (label, repeat
  days, vibrate, fade-in, ringtone, snooze duration), which opens as a tall bottom
  sheet with an always-visible floating Save button rather than a scrolling screen.
- **Fade-in is on by default.** Alarms ramp from near-silent to full volume over the
  first 45 seconds; toggle it per-alarm in the options sheet.
- **A snoozed alarm shows a "Snoozed until HH:MM" badge** on its row until it rings
  again or is otherwise cleared.
- **10 minutes before an alarm**, a heads-up notification appears with a live
  countdown and a "Skip" action, so you can cancel that occurrence if you're already
  awake (repeating alarms just skip that one occurrence; one-off alarms get disabled).
- Uses plain `MaterialTheme` on stable `material3` (pinned to `1.4.0` in
  `gradle/libs.versions.toml`, overriding the Compose BOM's own suggestion, which
  currently maps to an older 1.3.x). Material 3 Expressive's actual theme wrapper
  (`MaterialExpressiveTheme`, `MotionScheme.expressive()`) isn't public API yet as of
  1.4.0 stable — it's `internal` there, only promoted to public API starting in
  `1.5.0-alpha19`. Switch `material3` to that alpha (or later) and swap
  `ui/theme/Theme.kt`'s `MaterialTheme` call for `MaterialExpressiveTheme` if you'd
  rather have the real thing on a pre-release library than wait for it to stabilize.

## Project layout

```
app/src/main/java/com/targetcrafter/haalarmclock/
  data/     Room entity/DAO + AlarmRepository (source of truth for alarms)
  alarm/    AlarmManager scheduling, the BroadcastReceiver that fires alarms,
            the ringing foreground service + full-screen UI, boot rescheduling
  ha/       HA settings storage, the REST push client, the command WebSocket
            client, and the foreground service that ties them together
  ui/       Jetpack Compose screens (alarm list, editor, settings) + ViewModels

custom_components/ha_alarmclock/   Home Assistant custom integration (Python)
  __init__.py     sets up the REST view + forwards to the platforms below
  http.py         POST /api/ha_alarmclock/sync — receives pushes from the phone
  store.py        in-memory per-device state, rebuilt from the next push after restart
  config_flow.py  single-step "confirm setup" flow (no secrets collected on the HA side)
  binary_sensor.py, sensor.py, switch.py, button.py   the entities themselves
```

## Setting up the Home Assistant side

1. Copy `custom_components/ha_alarmclock/` into your HA config's `custom_components/`
   directory (or install it via HACS as a custom repository — `hacs.json` is already
   set up for that), then restart Home Assistant.
2. Settings → Devices & Services → Add Integration → search "HA Alarm Clock". There's
   nothing to fill in; it just needs to be added once.
3. In your HA profile (bottom left → your name) → Security tab → generate a
   **Long-Lived Access Token**.
4. In the Android app's Settings screen, enter your Home Assistant URL and that token,
   and enable sync. A device (with its alarms as switches, a ringing sensor, a next-alarm
   sensor, and snooze/dismiss buttons) appears in HA the first time it pushes.

Only one instance of the integration is needed even with multiple phones — each
device that pushes to it shows up as its own HA device automatically.

## Building the Android app

Requires JDK 17+, the Android SDK (compileSdk 35), and network access to
`dl.google.com`/Maven Central for AndroidX, Compose, and Room. This project was
scaffolded in a sandboxed environment without Android SDK/network access to Google's
Maven repo, so the build has **not** been verified end-to-end with
`./gradlew assembleDebug` — please do that first thing in a normal dev environment or
CI (e.g. via `android-actions/setup-android` in GitHub Actions) and fix up anything
that doesn't compile.

```
./gradlew assembleDebug
```

The Python integration's structure *has* been validated: all modules import cleanly
against a real installed `homeassistant` package (2024.3.3), and `store.py`'s payload
handling and the entity classes were exercised directly (construction, dynamic
add/remove of alarm switches, timestamp parsing). What's untested is the full runtime
path inside an actual Home Assistant instance (config flow with a real `hass`, the
HTTP view wired through HA's auth middleware, the dispatcher signals end-to-end) —
worth a real smoke test before relying on it.

## Permissions

- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — required to use `setAlarmClock()`.
- `POST_NOTIFICATIONS` — for the ringing alarm and the (minimum-priority) HA sync
  status notification.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — the ringing service plays the alarm sound.
- `FOREGROUND_SERVICE_DATA_SYNC` — the HA sync service keeps a network connection.
- `RECEIVE_BOOT_COMPLETED` — alarms and the HA connection are re-armed after reboot.

## Not yet implemented

Timers, stopwatch, and world clock (this first pass is alarms-only, matching the
stock Clock app's alarm tab). The HA access token is stored in
`EncryptedSharedPreferences`. The integration's device state lives in memory only —
it's rebuilt from the phone's next push after a Home Assistant restart rather than
persisted to disk.
