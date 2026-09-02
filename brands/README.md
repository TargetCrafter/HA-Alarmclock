# Brand images

Home Assistant does **not** take an integration's icon from the integration's own repository — it
resolves it from [home-assistant/brands](https://github.com/home-assistant/brands) by domain, and
falls back to a generic placeholder when there's no entry. So these files can't do anything from
here; they're kept in this repo as the ready-to-submit source of truth, matching the layout the
brands repo expects.

## What's here

`custom_integrations/ha_alarmclock/` — the domain folder name matches `manifest.json`'s `domain`.

| File | Size | Notes |
| --- | --- | --- |
| `icon.png` | 256×256 | Standard resolution |
| `icon@2x.png` | 512×512 | High-DPI |

Both are PNG with transparency, trimmed of empty space and padded to the required 1:1 square.

They're derived from the Android launcher icon's foreground layer
(`app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png`) with one deliberate change: the
adaptive icon draws the tree/circuit lines as *transparent knockouts* that let its white background
layer show through. That layer doesn't exist here, so those knockouts are filled with real white —
otherwise the lines would be see-through and vanish against Home Assistant's dark theme.

No `logo.png` is submitted: logos are for wordmarks, and this project doesn't have one. Home
Assistant falls back to the icon wherever a logo would be used.

## Submitting them

1. Fork [home-assistant/brands](https://github.com/home-assistant/brands).
2. Copy `custom_integrations/ha_alarmclock/` from here into the fork's `custom_integrations/`.
3. Open a PR against the brands repo.

Note that brands requires custom integrations not to use Home Assistant branding in their images,
so that end users can't mistake them for official integrations — this icon is the app's own, so
that's fine.

## Regenerating

The conversion is scripted in the commit that added these files; the short version is: fill the
interior knockouts white, trim to the glyph, pad to a square, then resize to 256 and 512 with
Lanczos. Re-run it if the app's launcher icon ever changes, and open a new brands PR — updating
these files here has no effect on what Home Assistant displays until that PR merges.
