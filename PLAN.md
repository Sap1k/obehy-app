# Oběhy native Android app: architecture & first-milestone plan

## Context

Oběhy (`sap1k/obehy`) is a Czech realtime backend (FastAPI `/api/v1`, per-feed GTFS-RT) with a
React + MapLibre web app. The native app (`sap1k/obehy-app`, an empty repo on branch
`claude/awesome-allen-3hjyfu`) was planned as a post-1.0 project and is now moving forward. The goal
is one Android app that combines international journey planning (MOTIS/Transitous) with Oběhy's
Czech realtime, vehicle and operational data. It has three modes, **Oběhy**, **Transitous + Oběhy**
and **Transitous only**, built on the same data layer.

**MOTIS figures out how to get you there. Oběhy tells you everything it knows about the vehicles
getting you there.**

## Feasibility verdict: high

The main risk was matching MOTIS legs to Oběhy trips. Research shows it is mostly a solved problem:

- **Transitous already ingests Oběhy's own feeds** (`feeds/cz.json` in public-transport/transitous):
  `motis.obehy.cz/get-feeds/cz-czptt-gtfs.zip` + `cz-czptt-gtfs-rt.pb` (dataset `CZPTT`) and
  `cz-jdf-filtered-gtfs.zip` + `cz-jdf-gtfs-rt.pb` (dataset `JDF-merged`). Czech rail and CIS-bus
  legs from Transitous therefore carry **Oběhy's own GTFS `trip_id`s**, prefixed with the
  dataset tag. motis.obehy.cz consumes the same files (`src/obehy/release/feed_list.py`).
- Transitous's Czech realtime **already is** Oběhy's GTFS-RT, so even unenriched Czech legs
  carry Oběhy delays. Enrichment adds what GTFS-RT can't carry: vehicle identity and photo,
  composition, platform evidence, call `status`/`source_class`, circulation and alerts.
- PID and IDS JMK legs come from their own GTFS in Transitous. The backend already resolves source
  keys through `GET /api/v1/trips/by-key/{namespace}/{key}/{date}` (`pid:gtfs_trip_id` exists,
  `src/obehy/api/trips.py:48-75`). IDS JMK/IDZK/DPMO/PMDP may have no namespace yet, so they
  degrade gracefully.
- Release skew: Transitous may run an older release, and its `trip_id` may belong to a superseded
  timetable version. The API already answers **410 + journey key + `by_key` path** for that case
  (`trips.py:~272`), which the resolver follows.

Combining both "apps" is the same app with a mode flag. The modes differ only in navigation,
visible features and defaults. Repositories, models and the enrichment pipeline are shared.

## Decisions (this session)

- **Onboarding asks for one of three modes**, changeable in Settings and kept in DataStore: Oběhy
  (motis.obehy.cz with Oběhy data), Transitous + Oběhy, and Transitous only, which never contacts
  Oběhy and can point at any MOTIS server. Servers don't publish their OpenAPI, so a wizard tries
  the endpoints the app uses before a server can be picked; modes stay locked until it's reset.
- **Enrichment uses a new batch endpoint in obehy.** The backend owns MOTIS-ID parsing and
  the dataset-tag → namespace mapping, which keeps the "IDs are opaque, resolve through
  source_key" rule server-side.
- **The first milestone is Planner + enrichment.**
- **Czech + English** string resources from day one.

## Architecture

```
┌─ presentation (Compose, Material 3 adaptive) ─────────────────────────────┐
│ onboarding · journey search · results · itinerary · trip · board · map     │
│ ModeConfig decides nav destinations & which panels render                  │
├─ domain (pure Kotlin) ─────────────────────────────────────────────────────┤
│ Itinerary/Leg/Place/StopEvent (provider-neutral) · LegEnrichment (sealed:  │
│ Resolved | Unavailable | Pending) · EnrichItineraries use case             │
├─ data ─────────────────────────────────────────────────────────────────────┤
│ RoutingRepository ── MotisClient (base URL = Transitous | motis.obehy.cz)  │
│ ObehyRepository ──── ObehyClient (/api/v1, incl. new /journeys/enrich)     │
│ SettingsRepository (DataStore) · Room (recent places, saved journeys)      │
└────────────────────────────────────────────────────────────────────────────┘
```

Pipeline: `MOTIS /api/v5/plan` → map to domain `Itinerary` → render immediately → in parallel,
`POST /api/v1/journeys/enrich` with every transit leg's MOTIS `tripId` → merge per leg into
`StateFlow` → UI updates. Unresolved legs keep MOTIS data with a quiet "no Oběhy detail" marker.
Refresh/replanning uses MOTIS (re-plan / `trip` endpoint) and never runs in-house.

### Stack

- Kotlin, Jetpack Compose, Material 3 with `material3-adaptive` (`NavigationSuiteScaffold`,
  `ListDetailPaneScaffold`, window size classes), Navigation Compose with type-safe routes.
- Ktor client (OkHttp engine) + kotlinx.serialization. DTOs are generated from
  `obehy/web/openapi.json` and MOTIS `openapi.yaml` with openapi-generator
  (`kotlin`, `kotlinx_serialization`, models only) in a Gradle task. Thin clients are written by hand.
- Hilt for DI, DataStore for settings, Room for favourites/recents. Offline is out of scope for now.
- MapLibre Native Android (via the maplibre-compose wrapper) for itinerary and later live map,
  reusing the web's style/PMTiles (`web/src/features/map/style.ts`).
- minSdk 26, targetSdk latest. Gradle version catalog. Modules start small:
  `:app`, `:core:model`, `:core:network`, `:core:data`, `:core:ui`, `:feature:planner`,
  `:feature:trip`, `:feature:onboarding`. Boards and map modules come later.
- CI: GitHub Actions running `./gradlew lint test assembleDebug`.

## Milestone 1: Planner + enrichment

### Backend (`sap1k/obehy`, same branch name)

1. **Done** (obehy `claude/awesome-allen-3hjyfu`): `POST /api/v1/journeys/enrich`, body
   `{legs: [{trip_id, from_stop_id?, to_stop_id?}]}` (≤ 60), all MOTIS IDs verbatim. Per leg:
   `outcome` (`resolved` | `not_running` | `not_found` | `ambiguous` | `unknown_dataset` |
   `malformed`), with the trip, journey, line, realtime, vehicles, composition flag, alerts and
   the leg's calls (`leg_calls` tells whether they were trimmed to boarding→alighting).
2. Mapping lives in `src/obehy/data/api/motis-datasets-v1.toml`: Transitous tag
   `cz-CZPTT` → feed `czptt` (direct trip_id), `cz-JDF-merged` → `jdf` (direct), `cz-PID`
   → `by-key pid:gtfs_trip_id`, and motis.obehy.cz's own tags. One parser for the MOTIS tripId
   format, validated against real responses first (see Verification).
3. The `summary` is a compact subset of existing models in `src/obehy/api/schema.py`:
   per-call `estimate`/`status`/`source_class` for the leg's board/alight calls, `TripVehicle`,
   platform, active `Alert` ids and a composition-available flag. Reuse the SQL behind
   `trips.py` (`trip_journey`, `trip_by_key`) and follow the 410 → journey-key fallback.
4. Tests go in `tests/unit` + `tests/db`, plus a `web/openapi.json` regen. Add a `PROGRESS.md` log line.

### App (`sap1k/obehy-app`)

1. Gradle scaffold, version catalog, modules, Hilt, CI workflow, `values/` + `values-cs/` strings.
2. Onboarding: the mode. Settings screen to change it.
3. Place search (MOTIS geocode), journey search form, results list, itinerary detail
   (phone: stacked; tablet: `ListDetailPaneScaffold` results | itinerary + map).
4. Enrichment merge plus leg UI (realtime estimate, vehicle chip, platform, "detail unavailable").
5. Trip detail: Czech legs → `GET /api/v1/trips/{trip_id}/{date}`, foreign → MOTIS `trip`.
   The mode decides how much is shown (composition/circulation only in the modes with Oběhy).
6. Itinerary map with MapLibre (leg polylines from MOTIS `legGeometry`).

## Shipped since milestone 1

- **My location and nearby stops:** MOTIS `map/stops` grouped into stops, in the place picker. Coordinates
  go to the routing provider rounded to about 100 m, except for the journey search itself.
- **Search history:** Room, on the phone only, per provider, excluded from backups. `JourneySuggester`
  ranks past searches by recency, time of day, kind of day and where the phone is, and offers the way back.

- **Trip detail:** tapping a ride opens its whole trip: MOTIS `trip` for the schedule and path in every mode, and
  in the modes with Oběhy, `journeys/enrich` then `/trips/{id}/{date}` and `/composition` for live stops, notes,
  vehicle and train order. Circulation and delay history are still to come.

## Later milestones

- **M2 Boards:** MOTIS `stoptimes` (both modes, enriched via the same endpoint), plus the
  Oběhy `/stop-places/{id}/departures` board in the modes with Oběhy. Grouped nearby boards come later.
- **M3 Live map (modes with Oběhy):** `/api/v1/vehicles` viewport polling, vehicle detail, composition.
- **M4:** saved journeys with MOTIS refresh, disruption notifications, Play release.

## Verified against live services (2026-11-02)

- Both `api.transitous.org` and `motis.obehy.cz` serve MOTIS `/api/v6/plan`, `/api/v1/geocode` and `/api/v1/map/stops`.
- Leg `tripId` format: **`{YYYYMMDD}_{HH:MM}_{dataset tag}_{feed trip_id}`**. The date is the
  **GTFS service date** and the time the **local GTFS time** of the trip's first departure (it
  can pass 24:00, e.g. `24:01`), so the service date comes straight from the ID.
  - Transitous: `20261103_06:47_cz-CZPTT_czptt:trip:Pa%3A0054%3AKT----11435A%3A00%3A2026:1`
    (tags `cz-CZPTT`, `cz-JDF-merged`, `cz-PID`, `cz-IDS-JMK`, and `de-DELFI` for German legs;
    PID and IDS JMK trip IDs resolve on Oběhy by source key).
  - motis.obehy.cz: tags `czptt` and `jdf`. PID overlay trips arrive as `jdf` with Oběhy IDs
    (`jdf:trip:199993:260829:1227:overlay:…`), so they resolve directly.
  - Stop IDs follow the same `{tag}_{feed stop_id}` pattern (`cz-CZPTT_czptt:stop:CZ:53179:platform:2`).
- **Exact match works:** the stripped trip_id answered 200 from
  `obehy.cz/api/v1/trips/{trip_id}/2026-11-03` (R 696, journey `czptt:train_number` 696).
  The `%3A` is a literal part of the ID and must be URL-encoded again (`%253A`). Decoding it gives 404.
- motis.obehy.cz has CZ data only (no route to Dresden), as expected. Transitous returns mixed
  CZPTT + DELFI itineraries for Ústí → Dresden.

## Open risks to check early
- Filtered-JDF trip IDs from Transitous resolved on the live API in every sample checked; keep
  watching `not_found` rates once the app is in use.
- Transitous API usage policy (rate limits, attribution, User-Agent) for a public app.

## Verification

- Backend: `uv run pytest tests/unit -q` (+ `tests/db` with `OBEHY_TEST_DATABASE_URL`),
  `ruff`, `pyright`, `lint-imports`. Then a live check: plan Ústí → Praha → Dresden on
  Transitous, feed the leg tripIds to `/journeys/enrich`, expect the CZPTT/JDF legs `resolved`
  and the DE leg `unknown_dataset`.
- App: `./gradlew test lint assembleDebug`, plus unit tests for MOTIS → domain mapping and
  enrichment merge using recorded JSON fixtures. Run on an emulator at phone and tablet sizes and
  screenshot both layouts and both languages.
