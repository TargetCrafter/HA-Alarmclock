# HA Alarm Clock

An Android alarm clock and timer app that works fully offline like the stock Clock
app, with a world-clock tab and recolorable analog/digital home-screen widgets, and
optionally connects directly to Home Assistant — via a custom integration, no MQTT
broker required — so you can build automations around your alarms and timers (e.g.
turn on lights when an alarm rings, let HA snooze/dismiss it, or ask Assist to set an
alarm for you).

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
    path fires the same `create_alarm` command over the WebSocket channel, and the phone
    handles it in `HaSyncService`'s command collector. Repeat days are only settable via
    the service's `repeat` field (voice only ever creates a one-off alarm — free-form day
    parsing from speech was judged too fragile to ship).
    **A request with no label (the common case — plain "set an alarm for 7am" never sets
    one) updates the most recently created still-unnamed alarm's time/repeat instead of
    inserting a new one each time**, so repeatedly asking Assist to set an alarm doesn't
    pile up a new entity every time — it just keeps moving the same "unnamed" alarm
    around. A request *with* a label always creates a fresh alarm, and existing labeled
    alarms are never touched by this at all — only ever the label-less ones compete for
    reuse with each other.

## UI/UX notes

- **Clock, Alarms, Timers, and Stopwatch are separate bottom-nav tabs** (Clock first),
  swipeable with a `HorizontalPager` as well as tappable, with matching filled/outlined
  icon states (filled = selected, outlined = not, consistently across all four) so the
  bottom nav reads as one style rather than a mismatched one. The app reopens on
  whichever tab you had open last (`TabPreferencesStore`), not always the first one.
- **The top app bar, its settings entry point, and the "add" floating action button are
  shared chrome owned by `MainActivity`'s `TabsScreen`, not by each tab screen.** They
  sit in one `Scaffold` around the `HorizontalPager`, so they stay fixed in place while
  swiping between tabs — only the title text and the FAB's label track the current tab
  (e.g. "Add alarm" vs "Add timer" vs "Add time zone"; the Stopwatch tab has no "add"
  concept, so the FAB just doesn't render there — `Tab.addLabelRes` is nullable and the
  FAB lambda skips rendering when it's null, rather than repurposing the FAB for
  something unrelated). The settings entry point is a three-dot (`Icons.Filled.MoreVert`)
  button, not a gear. Each tab screen itself is now just its content (list/column) plus
  its own dialogs, taking `showAddDialog`/`onAddDialogDismiss` (or the alarm list's
  `showAddSheet`/`onAddSheetDismiss`) from the parent instead of owning a boolean of its
  own — keyed by *which page* requested it, so a swipe away from the tab that opened it
  can't leak "show add" into whatever tab lands underneath.
- **Every floating action button in the app uses the same icon+text
  `ExtendedFloatingActionButton` style** — the shared Add FAB, Settings' Save, and the
  alarm editor sheet's Save — instead of some being icon-only.
- **The Clock tab shows the live local time (with seconds), switchable between analog
  and digital in Settings**, plus an addable/removable list of other timezones —
  app-local only, never synced to HA. World clock rows stay plain digital text
  (`HH:mm` + a "tomorrow"/"yesterday" note when the date differs) regardless of that
  setting; only the big local-time display switches style. The analog face is drawn
  live with Compose's `Canvas` (`ui/clock/AnalogClockFace.kt`), styled to match the
  home-screen widget's face (see below): a filled disc (default
  `MaterialTheme.colorScheme.surfaceContainerHigh`, theme-adaptive rather than the
  widget's fixed color) with a bezel ring, uniform dimmed tick marks with the four
  quarter ones drawn longer, and a red second hand with a two-tone white-halo/red-center
  pivot dot — the one place in the app seconds are shown continuously, and the only part
  of the face that isn't theme-driven, deliberately, to read like a real clock's second
  hand.
- **The Stopwatch tab counts up in `MM:SS.mmm`**, with the time display pinned at the
  top and Start/Pause(Resume) plus a Lap/Reset button — full-width 96dp-tall buttons
  (`StopwatchButton` in `StopwatchScreen.kt`), the same height as the fullscreen ringing
  screen's Dismiss/Snooze — pinned at the very *bottom* instead, within easy one-handed
  reach; the lap list in between expands with `Modifier.weight(1f)` to fill whatever
  space is left, which is what pushes the buttons down. The Lap/Reset button switches
  meaning with state — Lap while running, Reset once paused (disabled at a fresh
  `00:00.000`, since there's nothing to reset yet). Laps are stored newest-first (a new
  one is prepended in `StopwatchViewModel`), each showing both its own split and the
  cumulative total at that point. The lap `LazyColumn` explicitly scrolls to index 0 (the
  newest lap) in a `LaunchedEffect(laps)` every time the list changes, rather than
  depending on the list's own default scroll-anchoring to keep the latest lap in view —
  that turned out not to be reliable on its own (tried first, still left Lap 1 pinned in
  view), so it's driven imperatively instead. `StopwatchViewModel` times off
  `SystemClock.elapsedRealtime()` rather than `System.currentTimeMillis()`, so it can't
  jump if the wall clock changes mid-run (NTP
  sync, timezone, DST, the user editing the time). It's a plain `ViewModel`, not backed
  by a foreground service or `AlarmManager` — a stopwatch has no completion to notify
  about, unlike a timer, so there's nothing worth a permanent notification for; it keeps
  running across tab swipes and a trip to Settings and back (both stay under the same
  `TabsScreen` back-stack entry) but not past the app process being killed.
- **Editing is in-place, not a separate screen.** Tap an alarm's time for a quick
  time-only popup — which now also has a Delete action, so removing an alarm doesn't
  require opening the full editor either — or tap its label/repeat area for the full
  options sheet (label, repeat days, vibrate, fade-in, ringtone, snooze duration),
  which opens as a tall bottom sheet with an always-visible floating Save button rather
  than a scrolling screen. Timers get a similarly lightweight add dialog (H/M/S
  steppers + an optional name), and each timer row's own Pause/Resume/Cancel/Dismiss
  stay their original compact size — a card in a scrollable list of possibly several
  timers has less room to spare than a screen with only ever one running stopwatch.
- **Fade-in is on by default.** Alarms ramp from near-silent to full volume over the
  first 45 seconds; toggle it per-alarm in the options sheet.
- **A snoozed alarm shows a "Snoozed until HH:MM" badge** on its row until it rings
  again or is otherwise cleared.
- **An hour before an alarm**, an ongoing (non-dismissable until skipped or it rings)
  notification appears with a live countdown and a "Skip" action, so you can cancel
  that occurrence if you're already awake (repeating alarms just skip that one
  occurrence; one-off alarms get disabled) — and so there's a clear, persistent heads-up
  well before the alarm, not just a brief one a few minutes out.
- **The fullscreen ringing screen uses large, full-width, stacked Dismiss/Snooze
  buttons** (96dp tall, distinct filled colors, an icon plus large text each) instead
  of two side-by-side outlined buttons, so they're easy to tell apart and hit
  accurately the moment you wake up.
- **Four home-screen widgets** — analog and digital clock, each in an opaque
  (background-filled) and a transparent (no-background) variant, all showing a live
  "Next: HH:mm" line for the soonest alarm, refreshed whenever alarms change. The
  transparent variants (`AnalogClockTransparentWidgetProvider`/
  `DigitalClockTransparentWidgetProvider`) are separate `AppWidgetProvider`s reusing the
  same layouts as their opaque counterparts — not a setting — so they show up as their
  own pickable entries in the system widget picker (like Android's own widgets do for
  their variants); `ClockWidgetUpdater` just skips the `setBackgroundColor` call for
  them, leaving the wallpaper showing through behind the analog widget's circle or the
  digital widget's text, with no rectangle behind either.
- The digital widget's next-alarm `TextView` is `match_parent` width with
  `android:gravity="center"` and up to 2 lines, not `wrap_content` — it used to be
  `wrap_content` with no width tied to its parent, so a long unbroken alarm label could
  inflate the whole `LinearLayout`'s measured width past the widget's actual bounds,
  pushing the time display above it out of view too, not just the alarm line itself.
  Now a long label wraps within the widget's real width instead.
- The digital widgets' `TextClock` still ticks on its own with no code involved; the
  analog widgets' face used to be the system `android.widget.AnalogClock`, but that
  rendered blank in practice (and, being a fixed system drawable, couldn't be
  recolored anyway) — it's now a full "watch face" drawn to a `Bitmap` by hand
  (`AnalogClockRenderer`) and pushed via `RemoteViews.setImageViewBitmap`: a filled disc
  in the background color with a subtle bezel ring; twelve dimmed tick marks
  (`foreground` at ~55% alpha) close to the rim, the four quarter ticks (12/3/6/9) drawn
  longer than the other eight so the face reads at a glance without numerals; white
  hour/minute hands; and **a red second hand with a two-tone white-halo/red-center pivot
  dot** — `ANALOG_CLOCK_SECOND_HAND_COLOR` (Compose) and `AnalogClockRenderer`'s
  `SECOND_HAND_COLOR` share the same `#E53935` so the app and widget match. The bitmap
  itself is identical for both variants — the disc supplies the "face" either way — but
  `ClockWidgetUpdater` gives the `ImageView` an 8dp `setViewPadding` on the opaque variant
  only, so the circle keeps a little breathing room inside its own rectangle, while the
  transparent variant (no rectangle to breathe against) fills the widget bounds right out
  to the edge.
- **When there's a next alarm, a centered badge — the Material "alarm" glyph (a clock
  face with bell "ears" and a hand inside, from `R.drawable.ic_alarm_glyph`, rasterized
  and tinted at runtime — the one non-Canvas-primitive element in the renderer; an
  earlier version reused the app icon's bell-with-cutout glyph, which read as Fluent
  rather than Material style) plus its bare `HH:mm` (no "Next:" prefix or label; the
  glyph already says "alarm") — sits just below the pivot,** backed by an opaque patch in
  the same background color as the disc, painted *after* the hands so any hand passing
  behind the badge is cleanly masked out rather than a line poking through between the
  glyphs. No badge is drawn at all when there's no next alarm. The digital widgets keep
  their own separate next-alarm text row underneath the time (still
  `Next: <label> · HH:mm`, hidden when there's no alarm) — only the analog badge was
  redesigned to sit centered-and-masked into the face.
- **The analog widgets have a static `previewImage`** (`drawable-nodpi/widget_preview_analog.png`,
  a 10:10 rendering of the same design at default colors) **instead of a live
  `previewLayout`**, so the system widget picker actually shows something — the picker
  only ever inflates the layout XML statically, and `widget_analog_clock.xml`'s
  `ImageView` has no default `android:src` (its content only ever exists as a bitmap
  pushed at runtime via code), so a `previewLayout` there rendered as a blank circle. The
  digital widget didn't need this fix since its `TextClock` renders real ticking content
  even in a statically-inflated preview.
- **Redrawing it every second — so the second hand actually moves — needs a foreground
  service (`AnalogWidgetTickerService`), not just `AlarmManager`.** An `AlarmManager`
  trigger (even the Doze-surviving `setExactAndAllowWhileIdle`) is what the first version
  of this used, ticking once a minute at first and then once a second, but Android
  throttles that API to roughly every few seconds for an app that isn't in the
  foreground — a platform ceiling, not a bug in how it was being scheduled. A foreground
  service isn't subject to that: a plain coroutine `delay(1000)` loop fires on schedule
  for as long as it's alive. The cost — and it's a real one, gone into with eyes open —
  is the low-priority, silent, ongoing "Keeping the analog widget's second hand live"
  notification Android requires any foreground service to show, plus a bit more battery
  use, for as long as an analog widget (opaque or transparent) is on your home screen.
  `AnalogWidgetTicker.ensureRunning`/`stopIfNoInstances` start and stop it — called from
  `onEnabled`/`onUpdate`/after reboot, and from `onDisabled`, tracking both analog
  provider variants together since a provider's own `onDisabled` only fires when *that
  specific* provider's last instance is removed, not the other variant's.
- **The analog widget's clock face is full-bleed** (a `FrameLayout` with the `ImageView`
  filling the whole widget, `widget_analog_clock.xml` no longer has a separate text
  view at all — the badge above is baked into the bitmap instead) **instead of being
  squeezed above a fixed text row**, so it's noticeably bigger. The digital widget's
  next-alarm row is likewise hidden entirely when there's no next alarm, instead of a
  permanent "No alarm set" placeholder.
- A since-removed "dynamic launcher icon" feature was also found to be crashing the app
  on every launch (`PackageManager.setComponentEnabledSetting` on an `activity-alias`
  failing inside an uncaught coroutine, fixed by removing that feature and giving
  `HaAlarmClockApp`'s background coroutine scope a `CoroutineExceptionHandler` that logs
  instead of crashing) — but that turned out to be a red herring for an *earlier* blank-
  widget report: the actual cause back then was `widget_analog_clock.xml`'s `ImageView`
  having `android:layout_width="0dp"` inside a *vertical* `LinearLayout`. `layout_weight`
  only distributes space along a `LinearLayout`'s main axis (height, here); on the cross
  axis a `0dp` width just stays a literal zero, so the face was always being measured and
  drawn into a zero-width view no matter how correct the bitmap it held was. Fixed by
  changing it to `match_parent` (superseded since by the `FrameLayout` rework above).
- **All four widgets' colors are configurable in Settings** — a background and a
  foreground (hands/text) color, each pickable from a preset swatch row or typed as a
  hex code, applied live via `RemoteViews.setInt(..., "setBackgroundColor", ...)` /
  `setTextColor`/the bitmap renderer (the transparent variants still honor the
  foreground color, just never the background one on the surrounding rectangle — the
  analog disc itself is always filled with the background color, on both variants).
  Default is near-black on off-white (`#1B1B1B` background, `#E8E8E8` foreground),
  matching a fairly typical dark watch face rather than the mid-grey used before; a
  "Reset to default" button restores that. One caveat: because the *rectangle's* color
  is applied as a plain `ColorDrawable`, the opaque widgets lose their rounded corners
  on Android 11 and below — Android 12+ launchers round/clip every widget's outer bounds
  automatically regardless of its own background, so this only matters pre-12 (moot for
  the analog widgets' own circular face either way, and for the transparent variants'
  rectangle, which has no fill to round in the first place).
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
  widget/   the analog/digital clock home-screen widgets (opaque + transparent variant
            of each) — AppWidgetProviders, AnalogClockRenderer (hand-drawn bitmap face),
            AnalogWidgetTickerService (the foreground service that redraws the analog
            face every second) + AnalogWidgetTicker (starts/stops it), ClockWidgetUpdater
            (pushes colors + next-alarm text into every instance of all four)
  ha/       HA settings storage, the REST push client, the command WebSocket
            client, and the foreground service that ties them together
  ui/       Jetpack Compose screens (alarm list, timer list, clock/world clocks,
            stopwatch, editor, settings) + ViewModels; MainActivity's TabsScreen is the
            HorizontalPager + bottom nav shared by all four main tabs; data/ holds
            the plain-SharedPreferences stores behind Settings' clock-style and
            widget-color pickers, the world-clock timezone list, and the
            last-open-tab memory (ClockPreferencesStore, WidgetAppearanceStore,
            WorldClockStore, TabPreferencesStore)

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
- `POST_NOTIFICATIONS` — for the ringing alarm, the (minimum-priority) HA sync status
  notification, and the (also minimum-priority) analog-widget-ticker notification.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — the alarm and timer ringing services play sound.
- `FOREGROUND_SERVICE_DATA_SYNC` — the HA sync service keeps a network connection.
- `FOREGROUND_SERVICE_SPECIAL_USE` — `AnalogWidgetTickerService`'s per-second redraw
  loop, since none of the predefined foreground service types (data sync, media
  playback, location, and so on) fit "keep a widget's second hand moving."
- `RECEIVE_BOOT_COMPLETED` — alarms, timers, the HA connection, and the analog widget
  ticker are all re-armed after reboot (neither AlarmManager entries nor a running
  service survive one on their own).

No new dangerous permission was needed for timers or the widgets.

## Not yet implemented

Alarms, timers, a stopwatch, widgets, a Clock tab with world clocks, and a custom app
icon (an adaptive icon: a flat-white vector background layer plus a gradient-filled
foreground rasterized from the provided SVG into density-specific PNG mipmaps, since
Android's vector `<gradient>` element only supports a true circular radial gradient and
can't reproduce the source's elliptical stretch) are all in. The HA access token field
in Settings is masked like a password field (with a tap-to-reveal toggle) and the token
itself is stored in `EncryptedSharedPreferences`; clock style, widget colors, and the
world-clock list are in plain (unencrypted) SharedPreferences, since none of it is
sensitive. The stopwatch doesn't persist across app-process death (it's a plain
`ViewModel`, not backed by any service — see the UI/UX notes above) and isn't synced to
HA in any way. The integration's device state lives in memory only — it's rebuilt from
the phone's next push after a Home Assistant restart rather than persisted to disk.
Timers aren't controllable *from* HA (only exposed as sensors) — only alarms have a
switch entity and HA→phone commands; symmetric timer control (pause/resume/cancel from
HA) would be a natural follow-up if wanted.
