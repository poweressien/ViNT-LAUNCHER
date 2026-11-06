# Architecture

**Deliberate deviation from the brief (item 12 "never silently change architecture"):** the brief suggests ~16 Gradle
modules. I built **one Gradle module (`:app`) with strict package boundaries** instead, because I could not compile in
my sandbox and multi-module wiring multiplies first-build failure points. Packages map 1:1 to the suggested modules and
can be split into Gradle modules later without code changes beyond `build.gradle.kts` files.

| Package | Suggested module | Role |
|---|---|---|
| `data` | launcher-core / application-manager / storage / settings | `AppRepository` (LauncherApps, icon cache, icon packs), Room DB, DataStore `SettingsStore`, `Organizer` |
| `ui.theme`, `ui.components`, `ui.home` | visual-engine / theme-engine | Palettes (9 themes), neumorphic modifier, icons, widgets, Classic home, dock, drawer, search |
| `engines` | floating / shuffle / orbital / three-dimensional | Floating, Shuffle, Orbital, Projected (pseudo-3D modes), Timeline, Adaptive |
| `engines.gl` | three-dimensional-engine | OpenGL ES 2.0: layouts, meshes, atlas, renderer, touch view |
| `studio` | customization-studio | Studio panel + controls |
| `gestures` | gesture-engine | Pointer + sensor gestures → `GestureAction` |
| `notifications`, `ui.overlays` | notification-companion / control-center | Listener service, in-memory store, notification centre, control centre |
| `lockscreen`, `messaging` | lockscreen-companion / messaging-companion | Companion activity + weather, SMS module |
| `widgets` | widget-engine | AppWidgetHost flow |
| `system` | — | `SystemActions` (public-API-only), `LockService` |

## Key decisions
- **Single source of truth:** `AppRepository.apps` (real `LauncherApps` data). Every engine reads `LauncherViewModel.visibleApps`;
  switching modes never re-adds anything. Meta (hidden/pinned/rename/launch counts) and layout are Room tables keyed by `pkg/activity`.
- **One ViewModel** (`LauncherViewModel`) shared by Main/Lock/SMS activities; manual service locator in `LauncherApp` (no DI library).
- **No per-frame recomposition:** engines advance one clock and read it only in `offset {}` / `graphicsLayer {}` lambdas (layout/draw phase).
- **3D = OpenGL ES 2.0** (not Filament): one 1024² icon atlas, one draw call for icons, optional lit body mesh. Chosen for zero extra dependency,
  broad low-end support, and small code surface. Filament would give PBR/glass but adds ~MBs and build risk. Evaluated, deferred.
- **Neumorphism without blur:** stacked translucent rounded rects → identical on every GPU/OEM and cheap while animating.
- **Privacy:** notification content/usage analysis stays in memory/on device; only network use is opt-in Open-Meteo weather (coordinates only).
- **Lifecycle:** GL view paused on `ON_PAUSE`; sensors registered only while resumed; no foreground/background services of our own.
