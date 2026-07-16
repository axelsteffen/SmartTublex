# SmartTublex Branding

Replace upstream SmartTube launcher/in-app branding with the SmartTublex logo during the wrapper APK build.

## Source assets

| File | Role |
|------|------|
| [`images/logo/smarttublex.png`](../images/logo/smarttublex.png) | Symbol master (no text) |
| [`images/logo/smarttublex_branded.png`](../images/logo/smarttublex_branded.png) | Master with required text |
| [`images/logo/generated/`](../images/logo/generated/) | Sized PNGs copied into the decoded APK |
| [`images/thumbnails/`](../images/thumbnails/) | Section card thumbnail masters |
| [`images/thumbnails/generated/`](../images/thumbnails/generated/) | Card-sized PNGs (`640×360`) copied into the decoded APK |

## Required text (exact typography)

| Line | Exact wording |
|------|----------------|
| Primary | `Beta Version` (never shorten to `BETA`) |
| Disclaimer | `unofficial SmartTube fork` |

If space is tight, drop the disclaimer — do **not** rename the primary line.

## How it is applied

`packageWrapperApk` in [`app/build.gradle.kts`](../app/build.gradle.kts), after apktool decode and manifest patch, before rebuild:

1. Copy `images/logo/generated/*.png` over `decoded/res/mipmap-nodpi/` (and density `ic_launcher.png` where present).
2. Copy `images/thumbnails/generated/{all_movies,all_tv_shows}.png` into `decoded/res/drawable-nodpi/`.
3. Patch `decoded/res/values/strings.xml`: `app_name` (and `browse_title` if present) → `SmartTublex`.

Library browse stubs (`PlexMediaItemAdapter.fromLibraryBrowse`) load those drawables via:

`android.resource://org.smarttube.beta/drawable/all_movies` / `all_tv_shows`

Upstream SmartTube sources are not modified. The `:app` stub manifest does not need `android:icon`.

## Size matrix

| Asset | Size | Text |
|-------|------|------|
| `app_banner.png` | 640×360 | Symbol + `Beta Version` + `unofficial SmartTube fork` |
| `app_icon.png` / `app_icon_alt.png` | 320×320 | Symbol + `Beta Version` (+ disclaimer if readable) |
| `app_logo*.png` | 180×180 | Symbol + `Beta Version` |
| `ic_launcher` (mdpi–xxhdpi) | 48–144 | Symbol; `Beta Version` only when readable |
| `all_movies.png` / `all_tv_shows.png` | 640×360 | Section card art (no text overlay) |

## Regenerating assets

```bash
images/logo/.venv/bin/python images/logo/generate_branding.py
images/logo/.venv/bin/python images/thumbnails/generate_thumbnails.py
```

Requires Pillow (see `images/logo/.venv` or `pip install Pillow`).
