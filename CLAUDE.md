# Oběhy for Android: rules

Read this before changing anything. Most rules here are enforced by the build (`spotlessCheck`, `lintDebug`,
the convention plugins); the rest are on you. [PLAN.md](PLAN.md) has the product architecture and milestones.

## Modules and who may depend on whom

```
app ──► feature:* ──► core:data ──► core:network ──► core:model
  │          ├──────► core:map ──► core:ui
  └──────────┴──────► core:ui
```

`core:ui` and `core:map` use `core:model`'s types too.

- `core:model`: plain Kotlin domain types, serializable so a session can be saved. No Android, no network types.
- `core:network`: Ktor clients and DTOs. DTOs never leave this module; map them to `core:model` types.
- `core:data`: repositories, DataStore, Room (`obehy.room`, schemas in `core/data/schemas`) and `SessionStore` (what a screen
  needs to come back after the app is killed), bound with Hilt. Screens get data only through repositories.
- `core:ui`: **the design system**: theme, tokens, shared components. The only place styling is defined.
- `core:map`: the MapLibre map (`RouteMap`). Its base style is the web's (`repo/web/src/features/map/style.ts`),
  exported to `core/map/src/main/assets` by `node core/map/tools/export-style.mjs <web folder>`; rerun it
  when the web style changes, never edit the JSON by hand. `PreloadRouteMaps` draws the header maps of the results
  around what's on screen, off screen, so a journey opens with its map in place; a header without one loads a live `RouteMap`.
- `feature:*`: one user-facing area each (screens, view models, its routes). **A feature never depends on
  another feature** (the build fails if it does). Shared code goes down into a core module; moving between
  features is navigation, wired in `app`.
- `app`: the activity, the navigation shell (`ObehyShell`), Hilt application.
- `lint-rules`: our Android Lint checks (below).

Build configuration lives in the convention plugins (`build-logic/convention`); modules only apply them and set
a namespace. Every version is in `gradle/libs.versions.toml`, never in a module.

## UI rules

The look is defined once, in `core:ui`, and everything else reads it:

| Need | Use | Defined in |
|---|---|---|
| Colour | `MaterialTheme.colorScheme.<role>` | `Theme.kt` |
| Text style | `MaterialTheme.typography.<style>` (Manrope) | `Type.kt` |
| Gap, padding | `ObehyTheme.spacing.{xs,s,m,l,xl,xxl,xxxl}` | `Dimens.kt` |
| Size | `ObehyTheme.sizes.<name>` (add one if missing) | `Dimens.kt` |
| Shape | `MaterialTheme.shapes`, `MaterialShapes`, or the component's default | Material |
| A screen | `ObehyScreen(title) { … }`: top bar, insets, readable width | `ObehyScreen.kt` |
| A screen led by a map | `ObehyHeroScreen(title, hero = { … }) { … }`: the hero scrolls away with the content | `ObehyHeroScreen.kt` |
| Chips in a row | `ScrollingChipRow { … }`: scrolls sideways, edge to edge, fades where more wait | `components/` |
| A list arriving | `StaggeredEntry(order, enabled) { … }`: items rise in one after another | `components/` |
| One thing on two screens | `SharedBounds(key) { … }` on both: it morphs between them | `SharedMotion.kt` |
| Pick-one options | `ChoiceList` | `components/` |
| A card's background | `ObehyTheme.cardColor`: stands off the page in both themes | `Dimens.kt` |
| A state shown by a shape | `MorphingBadge(shape, color) { … }`: morphs in the layer alone | `ShapeMorph.kt` |
| Pull to refresh | `ObehyPullToRefresh`: Material's loading indicator | `components/` |
| Nothing to show, or a failure | `StateMessage(icon, text, action)` | `components/` |
| A card that gives under the finger | `pressScale(source)` with `Modifier.scaledBy` | `PressScale.kt` |
| Logo | `ObehyLogo()` | `Logo.kt` |

- Lint fails the build on a colour, `sp`, `dp` (other than `0.dp`), hand-made shape, `TextStyle`/`FontFamily`, a
  text literal in `Text(…)`/`contentDescription`, or a button without `shapes` anywhere outside `core:ui`
  (`ObehyRawColor`, `ObehyRawTypography`, `ObehyRawDimension`, `ObehyRawShape`, `ObehyHardcodedText`,
  `ObehyStaticButton`). Fix it by using the theme; if the theme lacks it,
  add a token or component to `core:ui`. `@SuppressLint("…")` only for a real exception, with a comment saying why.
- Don't pass colours, paddings or text styles into a core component to restyle it per screen. If a variant is
  needed, add it to the component as a named option.
- Every screen starts from `ObehyScreen`, which keeps content clear of the status and navigation bars. Only full-bleed
  flows (onboarding) use their own `Scaffold`.
- Material 3 **Expressive** components (`SegmentedListItem`, `ButtonDefaults.shapesFor`, `MaterialShapes`,
  `LargeFlexibleTopAppBar`, the wavy progress indicators) are the default; the opt-ins are set in the build, so no
  `@OptIn` is needed for them.
- Expressive stays where it helps: the journey under way and the search button are the app's big moments, and
  there shouldn't be more than these two. One strongly emphasized thing per screen. Abstract shapes only as decoration
  or to mark a change of state, never on the timeline, the journey bar or line badges. Springs (`MaterialTheme.motionScheme`)
  for all motion, fast for small things and slow for whole screens; only what the back gesture scrubs stays on a tween
  (`SharedMotion.kt` says why).
- Brand (Figma "Oběhy – marketing"): Lime marks a selected or current state, and the main action on dark, always with
  dark text on it. On light the main action is Petrol, as Lime would vanish into Paper. Route and operator colours are data: show them as they come, never recoloured to the brand. Never let colour
  alone carry a status (delay, cancellation): pair it with text or an icon.

## Writing a screen

1. In the feature module: a `@Serializable` route and a `NavGraphBuilder.xScreen()` extension.
2. A `@HiltViewModel` exposing one `StateFlow` of the screen's state, and functions for the user's actions.
3. A private `XDestination(viewModel = hiltViewModel())` that collects the state and calls a stateless
   `XScreen(state, onAction…, modifier)`. Only the destination knows the view model (see `SettingsScreen.kt`).
4. Register the route in `ObehyShell` (in `app`).

Every composable that draws something takes `modifier: Modifier = Modifier` as its first optional parameter and
applies it to its root. Spotless (with the Compose rules) checks this and more.

## Words

- Every string is a resource with an English (`values/strings.xml`) and a Czech (`values-cs/strings.xml`) version;
  lint fails on a missing translation.
- Write like a friendly, plain-spoken person: short sentences, "you", no jargon, no internals. Say what something
  does for the user ("Covers most of Europe"), not how it works ("MOTIS instance").
- Czech uses vykání ("Vyberte", "Kdykoli to změníte").
- No "·" between pieces of information. Give each piece its own element, style and spacing; use `LineBadge` for a line,
  `→` for where something goes, and a comma or a proper sentence in running or spoken text.

## Commands

Use Android Studio's Java 25 (the Gradle daemon asks for it):

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew spotlessApply                       # format before committing
./gradlew spotlessCheck                       # on its own: it can trip over build/ while the build writes it
./gradlew testDebugUnitTest :core:model:test :core:network:test :lint-rules:test lintDebug assembleDebug
```

Check UI changes on the phone (connected over wireless adb), not an emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Judge smoothness on the `profile` build (`./gradlew :app:assembleProfile`, then
`app/build/outputs/apk/profile/app-profile.apk`): a release build signed with the debug key. Debug
builds run Compose far too slowly to tell.

## Never

- add a dependency or version anywhere but `gradle/libs.versions.toml`;
- configure Android or Kotlin in a module's `build.gradle.kts` beyond its namespace and dependencies: change the
  convention plugin;
- add a colour, font, size or shape outside `core:ui`;
- put user-visible text in code.
