# SmartTublex Branding

Replace upstream SmartTube launcher/in-app branding with the SmartTublex logo during the wrapper APK build.

## Source assets

| File | Role |
|------|------|
| [`logo/smarttublex.png`](../logo/smarttublex.png) | Symbol master (no text) |
| [`logo/smarttublex_branded.png`](../logo/smarttublex_branded.png) | Master with required text |
| [`logo/generated/`](../logo/generated/) | Sized PNGs copied into the decoded APK |

## Required text (exact typography)

| Line | Exact wording |
|------|----------------|
| Primary | `Beta Version` (never shorten to `BETA`) |
| Disclaimer | `unofficial SmartTube fork` |

If space is tight, drop the disclaimer — do **not** rename the primary line.

## How it is applied

`packageWrapperApk` in [`app/build.gradle.kts`](../app/build.gradle.kts), after apktool decode and manifest patch, before rebuild:

1. Copy `logo/generated/*.png` over `decoded/res/mipmap-nodpi/` (and density `ic_launcher.png` where present).
2. Patch `decoded/res/values/strings.xml`: `app_name` (and `browse_title` if present) → `SmartTublex`.

Upstream SmartTube sources are not modified. The `:app` stub manifest does not need `android:icon`.

## Size matrix

| Asset | Size | Text |
|-------|------|------|
| `app_banner.png` | 640×360 | Symbol + `Beta Version` + `unofficial SmartTube fork` |
| `app_icon.png` / `app_icon_alt.png` | 320×320 | Symbol + `Beta Version` (+ disclaimer if readable) |
| `app_logo*.png` | 180×180 | Symbol + `Beta Version` |
| `ic_launcher` (mdpi–xxhdpi) | 48–144 | Symbol; `Beta Version` only when readable |

## Regenerating assets

```bash
logo/.venv/bin/python logo/generate_branding.py
```

Requires Pillow (see `logo/.venv` or `pip install Pillow`).
