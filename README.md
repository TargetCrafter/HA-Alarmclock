# HA Alarm Clock

An Android alarm clock and timer app that works fully offline like the stock Clock
app, with a world-clock tab, recolorable analog/digital home-screen widgets, and a
dynamic clock launcher icon, and optionally connects directly to Home Assistant — via
a custom integration, no MQTT broker required — so you can build automations around
your alarms and timers (e.g. turn on lights when an alarm rings, let HA snooze/dismiss
it, or ask Assist to set an alarm for you).

## How it works

- **Alarms are 100% local.** They're stored in a Room database and scheduled with
  `AlarmManager.setAlarmClock()` — the same API the stock Clock app uses, so alarms
  fire exactly on time, survive Doze, and don't need the "schedule exact alarms"
  permission dance. If Home Assistant is unreachable, alarms still ring.
- **Timers are 100% local too.** A running timer's single `AlarmManager` trigger
  (`setExactAndAllowWhileIdle`) is what actually fires it — no foreground service has
  to stay alive just to count down. The "time remaining" notification uses
  `NotificationCompat.setUsesChronometer`, which the system ticks on its own; a
  foreground service (`TimerService`) only exists for the ringing phase once a timer
  actually hits zero, mirroring `AlarmRingService` but simpler (no fade-in/snooze).
  Pause/Resume/Cancel are available right from that notification, not just in-app.
- **Home Assistant sync is optional and additive**, talking directly to a `HA Alarm
  Clock` custom integration (see `custom_components/ha_alarmclock/`) using a normal
  Home Assistant **long-lived access token** — the same credential type used by any
  other HA API client, generated once from your HA user profile. No MQTT broker, no
  separate webhook secret.
  - **Phone → HA (state):** a foreground service (`HaSyncService`) POSTs alarm/timer/
    ringing state to the integration's `/api/ha_alarmclock/sync` endpoint whenever it
    changes — purely push-based, no polling, ever. The integration turns this into
    entities: one `switch` and next-trigger `sensor` per alarm, one trigger-time
    `sensor` per timer, a `binary_sensor` for whether an alarm is ringing, and a
    device-wide `sensor` for the soonest alarm across all of them — all grouped under
    one HA device per phone. A timer's *remaining* time isn't exposed to HA at all —
    it changed too often to push at a reasonable rate without turning this into a
    polling integration, and simply wasn't a useful HA-side signal once considered
    (only its trigger time is genuinely useful for automations).
  - **Per-alarm and per-timer entities are keyed by a reused "slot" number, not the
    phone's own alarm/timer id.** The phone's Room database hands out ever-increasing
    ids, but HA entities are keyed by a small stable slot (`alarm_1`, `alarm_2`, ...;
    `timer_1`, `timer_2`, ... independently) that `store.py`'s
    `AlarmClockStore._reassign_slots` reuses for the next new alarm/timer once a slot's
    occupant is deleted — so the entity's `unique_id`/name update in place instead of a
    new entity being minted, the same way editing an alarm's time updates its existing
    entity. Deleting an alarm/timer just makes its slot's entities go unavailable until
    something new claims that slot; the number of orphaned/unavailable entities is
    bounded by the highest number of alarms/timers you've ever had *at once*, not the
    total number ever created.
  - **HA → phone (commands):** the same service also opens Home Assistant's built-in
    WebSocket API (`/api/websocket`) and subscribes to a custom `ha_alarmclock_command`
    event, which the integration fires when you flip a switch, press one of the
    `Snooze`/`Dismiss` button entities it creates, or call the `ha_alarmclock.create_alarm`
    service. The connection reconnects with backoff if it drops.
  - **Assist can create alarms.** The integration registers a `ha_alarmclock.create_alarm`
    service (callable from automations/scripts, or exposed as a tool to an LLM-based
    conversation agent) and ships `custom_sentences/en/ha_alarmclock.yaml` so the
    built-in keyword-based Assist pipeline understands sentences like *"set an alarm for
    7am"* or *"wake me up at 6:30 for gym"* out of the box — no LLM required. Either
    path fires the same `create_alarm` command over the WebSocket channel, and the
    phone inserts a brand-new alarm exactly as if you'd tapped + in the app. Repeat days
    are only settable via the service's `repeat` field (voice only ever creates a
    one-off alarm — free-form day parsing from speech was judged too fragile to ship).

## UI/UX notes

- **Alarms, Timers, and Clock are separate bottom-nav tabs**, each with their own
  Settings entry point; switching tabs preserves each screen's state instead of
  resetting it.
- **The Clock tab shows the live local time (with seconds), switchable between analog
  and digital in Settings**, plus an addable/removable list of other timezones —
  app-local only, never synced to HA. World clock rows stay plain digital text
  (`HH:mm` + a "tomorrow"/"yesterday" note when the date differs) regardless of that
  setting; only the big local-time display switches style. The analog face is drawn
  live with Compose's `Canvas` (`ui/clock/AnalogClockFace.kt`), including a second
  hand — the one place in the app seconds are shown continuously.
- **Editing is in-place, not a separate screen.** Tap an alarm's time for a quick
  time-only popup; tap its label/repeat area for the full options sheet (label, repeat
  days, vibrate, fade-in, ringtone, snooze duration), which opens as a tall bottom
  sheet with an always-visible floating Save button rather than a scrolling screen.
  Timers get a similarly lightweight add dialog (H/M/S steppers + an optional name).
- **Fade-in is on by default.** Alarms ramp from near-silent to full volume over the
  first 45 seconds; toggle it per-alarm in the options sheet.
- **A snoozed alarm shows a "Snoozed until HH:MM" badge** on its row until it rings
  again or is otherwise cleared.
- **10 minutes before an alarm**, a heads-up notification appears with a live
  countdown and a "Skip" action, so you can cancel that occurrence if you're already
  awake (repeating alarms just skip that one occurrence; one-off alarms get disabled).
- **The fullscreen ringing screen uses large, full-width, stacked Dismiss/Snooze
  buttons** (96dp tall, distinct filled colors, an icon plus large text each) instead
  of two side-by-side outlined buttons, so they're easy to tell apart and hit
  accurately the moment you wake up.
- **Two home-screen widgets** — analog and digital clock — both showing a live
  "Next: HH:mm" line for the soonest alarm, refreshed whenever alarms change. The
  digital widget's `TextClock` still ticks on its own with no code involved; the
  analog widget's face used to be the system `android.widget.AnalogClock`, but that
  rendered blank in practice (and, being a fixed system drawable, couldn't be
  recolored anyway) — it's now drawn to a `Bitmap` by hand (`AnalogClockRenderer`) and
  pushed via `RemoteViews.setImageViewBitmap`, redrawn once a minute by a
  self-rescheduling `AlarmManager` trigger (`AnalogWidgetTicker`) that's only ever
  armed while an analog widget instance actually exists (armed in `onEnabled`/
  `onUpdate`/after reboot, cancelled the moment the last instance is removed in
  `onDisabled`).
- **Both widgets' colors are configurable in Settings** — a background and a
  foreground (hands/text) color, each pickable from a preset swatch row or typed as a
  hex code, applied live via `RemoteViews.setInt(..., "setBackgroundColor", ...)` /
  `setTextColor`/the bitmap renderer. Default is grayscale (`#2E2E2E` background,
  `#E8E8E8` foreground) so the widgets blend into as many wallpapers/launchers as
  possible out of the box; a "Reset to default" button restores that. One caveat:
  because the color is applied as a plain `ColorDrawable`, the widgets lose their
  rounded corners on Android 11 and below — Android 12+ launchers round/clip every
  widget's outer bounds automatically regardless of its own background, so this only
  matters pre-12.
- **The launcher icon is a "dynamic" analog clock, hourly.** There's no public Android
  API for a third-party app to animate its own launcher icon in real time — the
  launcher caches icons as static bitmaps, and true live-ticking hands are a
  Pixel-Launcher-only trick reserved for Google's own Clock app. This uses the same
  technique "dynamic date" icon apps (e.g. calendar apps) rely on instead: twelve
  pre-rendered icon variants (`ic_launcher_clock_00`..`11`, one hour hand position
  each) wired up as `activity-alias` entries in the manifest, with exactly one enabled
  at a time. `DynamicIconUpdater` flips which one via `PackageManager
  .setComponentEnabledSetting`, on app start and on an hourly `WorkManager` periodic
  job (Android's 15-minute floor on periodic work, plus Doze/battery-optimization
  deferrals, mean "hourly" is a target, not a guarantee).
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
  data/     Room entities/DAOs + AlarmRepository/TimerRepository (source of truth)
  alarm/    AlarmManager scheduling, the BroadcastReceiver that fires alarms,
            the ringing foreground service + full-screen UI, boot rescheduling
  timer/    the timer counterpart to alarm/ — TimerScheduler, TimerReceiver
            (trigger + notification Pause/Resume/Cancel actions), TimerService
            (ringing phase only), TimerNotifications (the live countdown one)
  widget/   the analog/digital clock home-screen widgets — AppWidgetProviders,
            AnalogClockRenderer (hand-drawn bitmap face), AnalogWidgetTicker
            (its once-a-minute redraw trigger), ClockWidgetUpdater (pushes colors +
            next-alarm text into every instance of both)
  icon/     DynamicIconUpdater — the hourly dynamic launcher icon
  ha/       HA settings storage, the REST push client, the command WebSocket
            client, and the foreground service that ties them together
  ui/       Jetpack Compose screens (alarm list, timer list, clock/world clocks,
            editor, settings) + ViewModels; data/ holds the plain-SharedPreferences
            stores behind Settings' new clock-style and widget-color pickers, and the
            world-clock timezone list (ClockPreferencesStore, WidgetAppearanceStore,
            WorldClockStore)

custom_components/ha_alarmclock/   Home Assistant custom integration (Python)
  __init__.py     sets up the REST view + Assist + forwards to the platforms below
  http.py         POST /api/ha_alarmclock/sync — receives pushes from the phone
  store.py        in-memory per-device state, rebuilt from the next push after restart
  assist.py       the create_alarm service + intent handler for Assist
  services.yaml, custom_sentences/en/   service field docs; Assist sentence patterns
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
   and enable sync. A device (with its alarms as switches and next-trigger sensors, its
   timers as trigger-time sensors, a ringing sensor, a device-wide next-alarm sensor,
   and snooze/dismiss buttons) appears in HA the first time it pushes.

Only one instance of the integration is needed even with multiple phones — each
device that pushes to it shows up as its own HA device automatically. If more than one
phone has synced, `ha_alarmclock.create_alarm` needs its `device_id` field to say which
one; with just one phone, it's picked automatically.

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
against a real installed `homeassistant` package, and `store.py`'s payload handling —
including slot assignment/reuse for both alarms and timers — and the entity classes
were exercised directly (construction, entities going unavailable and being picked
back up when a new alarm/timer claims a freed slot, timestamp parsing). `assist.py`
went further: a real `homeassistant.core.HomeAssistant()` instance was started, the
`create_alarm` service was registered and called, and the `HaAlarmClockCreateAlarm`
intent was invoked through `homeassistant.helpers.intent.async_handle` directly — both
confirmed to fire the expected `ha_alarmclock_command` event with the right payload,
and the `custom_sentences/en/ha_alarmclock.yaml` sentence patterns were matched against
sample phrases with the real `hassil` recognizer (including the "called {label}"
variants, which needed HassIL's `[optional]` bracket syntax to stop the free-text time
slot from greedily swallowing the label — a plain pair of alternative sentence
templates picked the wrong match). What's still untested is the full runtime path
*inside* an actual Home Assistant instance end to end (config flow with a real `hass`,
the HTTP view wired through HA's auth middleware, a real Assist voice pipeline) — worth
a real smoke test before relying on it.

## Permissions

- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — required to use `setAlarmClock()`.
- `POST_NOTIFICATIONS` — for the ringing alarm and the (minimum-priority) HA sync
  status notification.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — the alarm and timer ringing services play sound.
- `FOREGROUND_SERVICE_DATA_SYNC` — the HA sync service keeps a network connection.
- `RECEIVE_BOOT_COMPLETED` — alarms, timers, and the HA connection are re-armed after
  reboot (AlarmManager entries don't survive one on their own).

No new dangerous permission was needed for timers, the widgets, or the dynamic icon;
`androidx.work:work-runtime-ktx` (added for the icon's hourly job) doesn't declare any
beyond what's already here.

## Not yet implemented

A stopwatch (alarms, timers, widgets, a dynamic icon, a Clock tab with world clocks,
and Assist integration are all in now). The HA access token is stored in
`EncryptedSharedPreferences`; clock style, widget colors, and the world-clock list are
in plain (unencrypted) SharedPreferences, since none of it is sensitive. The
integration's device state lives in memory only — it's rebuilt from the phone's next
push after a Home Assistant restart rather than persisted to disk. Timers aren't
controllable *from* HA (only exposed as sensors) — only alarms have a switch entity
and HA→phone commands; symmetric timer control (pause/resume/cancel from HA) would be
a natural follow-up if wanted.
