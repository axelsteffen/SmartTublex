# SmartTublex Branding

Replace upstream SmartTube launcher/in-app branding with the SmartTublex logo during the wrapper APK build.

## Source assets

| File | Role |
|------|------|
| [`images/logo/smarttublex.png`](../images/logo/smarttublex.png) | Full release logo (icon + wordmark + tagline) |
| [`images/logo/smarttublex_beta.png`](../images/logo/smarttublex_beta.png) | Full beta logo (same + Beta badge) |
| [`images/logo/smarttublex_branded.png`](../images/logo/smarttublex_branded.png) | Tall master derived from the beta logo |
| [`images/logo/generated/`](../images/logo/generated/) | Sized PNGs from the **beta** logo (copied into the decoded APK) |
| [`images/logo/generated_release/`](../images/logo/generated_release/) | Optional sized PNGs from the release logo |
| [`images/thumbnails/`](../images/thumbnails/) | Section card thumbnail masters |
| [`images/thumbnails/generated/`](../images/thumbnails/generated/) | Card-sized PNGs (`640×360`) copied into the decoded APK |

The masters already include all branding text. Generation only scales and letterboxes onto black — no text overlay.

## How it is applied

`packageWrapperApk` in [`app/build.gradle.kts`](../app/build.gradle.kts), after apktool decode and manifest patch, before rebuild:

1. Copy `images/logo/generated/*.png` over `decoded/res/mipmap-nodpi/` (and density `ic_launcher.png` where present).
2. Copy `images/thumbnails/generated/{all_movies,all_tv_shows}.png` into `decoded/res/drawable-nodpi/`.
3. Patch `decoded/res/values/strings.xml`: `app_name` (and `browse_title` if present) → `SmartTublex`.

Library browse stubs (`PlexMediaItemAdapter.fromLibraryBrowse`) load those drawables via:

`android.resource://org.smarttube.beta/drawable/all_movies` / `all_tv_shows`

Upstream SmartTube sources are not modified. The `:app` stub manifest does not need `android:icon`.

## Size matrix

| Asset | Size | Layout |
|-------|------|--------|
| `app_banner.png` | 640×360 | Content trimmed, then contain-fit (no crop) |
| `app_icon.png` / `app_icon_alt.png` | 320×320 | Full logo contain-fit |
| `app_logo*.png` | 180×180 | Full logo contain-fit |
| `ic_launcher` (mdpi–xxhdpi) | 48–144 | Full logo contain-fit |
| `all_movies.png` / `all_tv_shows.png` | 640×360 | Section card art (thumbnails script) |

## Regenerating assets

```bash
# Beta → images/logo/generated/ (default; used by packageWrapperApk)
images/logo/.venv/bin/python images/logo/generate_branding.py

# Also write release sizes → images/logo/generated_release/
images/logo/.venv/bin/python images/logo/generate_branding.py --variant both

images/logo/.venv/bin/python images/thumbnails/generate_thumbnails.py
```

Requires Pillow (see `images/logo/.venv` or `pip install Pillow`).
