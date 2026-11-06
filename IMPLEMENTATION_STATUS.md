# Implementation Status

**Read this first.** All code was written in a sandbox with **no Android SDK and no access to Google/Maven
repositories**, so **nothing has been compiled, run, or tested on a device.** Every "Written" below means
"code exists and was statically reviewed (braces/imports/API usage)", **not** "verified working".
First action on your side: push to GitHub and let `.github/workflows/build-apk.yml` build it, then send me the log
if it fails — expect some first-build compile errors.

Legend: **Written** = implemented in code, untested · **Partial** = works for a subset / with caveats ·
**Prototype** = rough · **Not implemented** · **Unsupported** = Android does not allow it.

Build result: **NOT RUN.** Unit/instrumented tests: **none written yet.** Devices tested: **none.**

## Milestone 1 — Foundation
| Feature | Status | Notes |
|---|---|---|
| Native Kotlin/Compose project, Gradle KTS, version catalog | Written | Single `:app` module with package-level separation (see ARCHITECTURE.md) |
| HOME intent filters, default-launcher flow | Written | RoleManager on Android 10+, Settings intent on 9 |
| Real app discovery + launch | Written | `LauncherApps`; package-visibility via `<queries>` (no QUERY_ALL_PACKAGES) |
| App drawer, sort (name/installed/usage/recent), search | Written | |
| Groups, dock, persistent layout | Written | Room; first-run auto-grouping by category/name heuristics |
| Reorder apps *inside* a group by drag | **Not implemented** | Use "Move to group…" |
| Drag-and-drop dock ordering | **Not implemented** | Add/remove only |
| Hide / restore / rename / pin / resize icon / uninstall / app info / shortcuts | Written | Long-press menu |

## Milestone 2 — Blue Mimicry visuals
| Feature | Status | Notes |
|---|---|---|
| Grey bg, neumorphic tiles & containers, red badges | Written | Drawn with stacked translucent rects (no blur) for GPU consistency |
| Floating groups with titles, big + small-cluster arrangement | Written | |
| Clock + animated blue wave widget, search bar, screen-time, search pill, dock | Written | Screen time needs Usage Access; shows a grant prompt, never fake numbers |
| **Compared against your screenshots** | **Not done** | Cannot render here. Needs on-device comparison; expect spacing tweaks |

## Milestone 3 — Display engines
| Engine | Status | Notes |
|---|---|---|
| Classic | Written | |
| Floating (collide, bounce, drag, pin, speed/intensity, pause) | Written | Circle collisions, per-frame separation |
| Auto-shuffle (4 s default, configurable, pinned excluded, taps locked while moving) | Written | Whole-screen only; per-group shuffle **Not implemented** |
| Orbital (rings, speeds, reverse, centre clock) | Written | |
| 3D cube/sphere/cylinder/ring/pyramid/carousel | Written | **Real OpenGL ES 2.0**; max 64 apps per shape (36 Lightweight); body colour/metal/glass, FOV, light, bg |
| "Custom geometric structures" | **Not implemented** | Six built-in structures only |
| Holographic, Galaxy, Matrix, Carousel, Magnetic, DNA, Liquid | **Prototype** | Compose pseudo-3D (perspective math), **not** OpenGL; Liquid is blobs + sway, not fluid sim |
| Timeline, Adaptive | Partial | Adaptive = time-of-day greeting + usage ranking; no automatic layout/theme switching |
| Presets | Partial | Saves mode/shape/theme/icon style/speeds, not full layout |

## Milestone 4 — Customization Studio
| Feature | Status | Notes |
|---|---|---|
| 9 themes + gradient presets | Written | "Custom user themes" = gradient presets only |
| Icon styles (9), per-app custom icon, ADW/Nova icon packs | Partial | Pack loader handles standard `appfilter.xml`; not tested against real packs |
| Wallpaper: image, video (in-launcher), animated gradient, blur | Partial | Blur needs Android 12+. Set system home/lock wallpaper via WallpaperManager |
| True live-wallpaper service | **Not implemented** | Opens the system live-wallpaper picker |
| Fonts: built-ins + import .ttf/.otf, scale | Written | Launcher-wide only. System font replacement: **Unsupported** |
| Animation settings | Partial | Intensity/duration/spring affect shuffle & liquid; app open/close, folder, page, hover, 3D-transition animation controls **Not implemented** |
| Layout controls | Partial | Icon size, dock slots/alpha, radius, shadow, container alpha, labels, widget toggles. Grid rows/cols, margins, spacing **Not implemented** |
| Live preview | Written | Studio is a half-height panel over the live home |

## Milestone 5 — System experience
| Feature | Status | Notes |
|---|---|---|
| Notification listener: real badges | Written | Needs Notification Access |
| Notification centre (group, expand, dismiss, actions, in-memory history, privacy hide) | Written | History is lost when the process dies (by design) |
| Dynamic-Island-style pill | Partial | Launcher-level only; shows latest notification / charging. No live-activity APIs |
| Custom notification sounds | **Not implemented** | |
| Floating notification popups outside the launcher | **Not implemented** | Would need overlay permission |
| Control Center | Partial | Torch, volume, DND (with policy access) work directly. Wi-Fi, Bluetooth, Airplane open system panels. Rotation needs WRITE_SETTINGS. Brightness is launcher-window only. Screen record only launches known OEM recorders |
| Lock-screen companion | Partial | Normal app screen, **not** the secure lock screen, never shown over the keyguard. Weather only after you enter coordinates |
| "Lock screen" action | Partial | Accessibility `GLOBAL_ACTION_LOCK_SCREEN`; user must enable the service |
| Status-bar styling | Written | Transparent bars + light/dark icons. Replacing the system status bar: **Unsupported** |
| SMS companion | Partial | Threads, send, search, pin, unread filter, default-SMS role + receivers. **No MMS**, no group messaging, no delivery receipts |

## Milestone 6 — Interactions
| Feature | Status | Notes |
|---|---|---|
| Gesture engine (tap/double/long/swipes/two-finger/pinch/shake/tilt → 15 actions) | Written | Swipes only start in edge zones to avoid fighting scroll/3D. No multiple "profiles" yet |
| AppWidgetHost widgets (add, configure, move, resize, remove) | Written | Placed on page 1; interactive via the host |
| Smart suggestions (usage-ranked, on-device) | Written | Heuristic scoring, no ML |
| Auto theme switching, time-based layout, intelligent re-organisation | **Not implemented** | |
| Universal search (apps, groups, settings, contacts) | Partial | App shortcuts and widgets are not searched |

## Milestone 7 — Optimisation
Perf profiles (FPS cap, body counts, sphere detail), reduced-motion, lifecycle pause for GL & sensors: **Written**.
Device-matrix testing, battery profiling, Android-version testing, release signing: **Not done.**
