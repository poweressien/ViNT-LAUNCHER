# Development Roadmap

## Immediate (blocking)
1. **First compile** via GitHub Actions; fix compile errors from the log.
2. Run on a real device; compare home screen against the reference screenshots; tune spacing, group positions, shadow strength, wave widget proportions.
3. Smoke-test: default-launcher flow, launch, drawer, search, long-press menu, edit mode, widgets, each engine, 3D touch/pinch, SMS, notification access.

## Next
- Drag-reorder inside groups and in the dock; drag app between groups.
- Per-group shuffle; custom 3D structures; more than 64 apps in 3D (multi-atlas).
- Remaining animation controls (open/close, folder, page, hover, 3D transitions); grid rows/cols/margins/spacing.
- Automatic theme switching & time-based layouts; full-layout presets; gesture profiles.
- Custom notification sounds; overlay-based floating popups (explicit permission).
- Real live-wallpaper `WallpaperService`; Filament evaluation for PBR/glass 3D.
- Split SMS + accessibility lock into a separate flavour for Play Store compliance.
- Release: real signing key, R8 verification, baseline profile, Play listing.

## Later
Unit tests (Organizer, layouts, suggestions scoring), Compose UI tests, macrobenchmark for frame time & battery.
