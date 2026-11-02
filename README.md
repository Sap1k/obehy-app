# Oběhy for Android

The native Oběhy app: international journey planning through MOTIS ([Transitous](https://transitous.org)
or motis.obehy.cz), with every Czech leg enriched by the [Oběhy](https://obehy.cz) API (live
delays, vehicles, compositions, alerts). See [PLAN.md](PLAN.md) for the architecture and milestones.

## Modules

| Module | What |
|---|---|
| `app` | Activity, Hilt application, the adaptive navigation shell |
| `core:model` | Plain Kotlin domain types (settings, leg enrichment) |
| `core:network` | Ktor clients and DTOs for MOTIS and the Oběhy API (JVM, unit-tested with recorded answers) |
| `core:data` | Repositories, DataStore settings, Hilt bindings |
| `core:ui` | Theme (the web's colour tokens) and shared components |
| `feature:onboarding` | First-start choice of mode and routing provider; Settings |
| `feature:planner` | Journey search (placeholder) |
| `feature:trip` | Trip detail (placeholder) |

Build configuration lives in the convention plugins under `build-logic/`; versions in
`gradle/libs.versions.toml`. Strings ship in English (`values`) and Czech (`values-cs`).

## Build

JDK 21 and the Android SDK (platform 36). Put `sdk.dir=...` in `local.properties` if
`ANDROID_HOME` is not set.

```bash
./gradlew :core:network:test testDebugUnitTest assembleDebug
```
