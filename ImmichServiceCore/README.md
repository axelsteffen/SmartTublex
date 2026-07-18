# ImmichServiceCore

Fork-only Immich API layer for [SmartTublex](https://github.com/axelsteffen/SmartTublex) (Android TV).

## Modules

| Module | Role |
|--------|------|
| `immichserviceinterfaces` | Immich service contracts and data interfaces |
| `immichapi` | Retrofit implementation, MSC adapters |

## Integration

Consumed by SmartTublex via Gradle project includes (same pattern as PlexServiceCore).
Intended to become a standalone git submodule once published separately.

```text
SmartTublex/
├── PlexServiceCore/       (submodule)
├── ImmichServiceCore/     (this tree)
└── app/
```

## Package

`de.developerleipzig.immichapi` / `de.developerleipzig.immichserviceinterfaces`

## Auth

Server URL + API key (`x-api-key`). Validate via `GET /api/users/me`.

## Tests

```bash
./gradlew :immichapi:testDebugUnitTest
```

Service tests use OkHttp MockWebServer (no live Immich server, no Robolectric).
