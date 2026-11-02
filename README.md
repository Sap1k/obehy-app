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
| `core:ui` | The design system: Material 3 Expressive theme in the Oběhy brand, spacing/size tokens, `ObehyScreen`, shared components |
| `feature:onboarding` | First-start choice of mode and routing provider; Settings |
| `feature:planner` | Journey search (placeholder) |
| `feature:trip` | Trip detail (placeholder) |
| `lint-rules` | Android Lint checks that keep colours, type, sizes, shapes and text out of everything but `core:ui` |

Build configuration lives in the convention plugins under `build-logic/`; versions in
`gradle/libs.versions.toml`. Strings ship in English (`values`) and Czech (`values-cs`).

The rules for working on the app (module boundaries, the design system, writing screens and copy) are in
[CLAUDE.md](CLAUDE.md).

## Build

JDK 21+ (Android Studio's bundled Java works) and the Android SDK (platform 37). Put `sdk.dir=...` in `local.properties` if
`ANDROID_HOME` is not set.

```bash
./gradlew spotlessCheck testDebugUnitTest :core:model:test :core:network:test :lint-rules:test lintDebug assembleDebug
```
