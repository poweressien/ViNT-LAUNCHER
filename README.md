# Blue Mimicry Launcher

A real Android launcher (Kotlin, Jetpack Compose, Room, DataStore, OpenGL ES) whose default look is a soft-grey
neumorphic "Blue Mimicry" home screen, with switchable experimental display engines (floating, auto-shuffle,
orbital, real 3D shapes, and more) over the same real installed-app list.

> **Status: code-complete draft, not yet compiled.** See `IMPLEMENTATION_STATUS.md` for an honest per-feature list.

## Build
Requirements: JDK 17, Android SDK 35.

```
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
No local SDK? Push to GitHub — `.github/workflows/build-apk.yml` builds the APK and uploads it as an artifact.

## Install & set as home
Install the APK → open **Blue Mimicry** → follow onboarding → "Set as default launcher" (Android's own dialog).
minSdk 28 (Android 9), targetSdk 35.

## Using it
- **Long-press empty home** → edit mode (drag groups, − / + resize, + Group, + Widget).
- **Long-press any app** → rename, pin, hide, change icon, move group, app info, uninstall, shortcuts.
- **Bottom ‹ Mode › label** → cycle display engines. **Studio** (long-press in non-Classic modes, or Edit → Studio) → everything else.
- **Edge swipes**: down from top / up from bottom (assignable). Shake and tilt also assignable.

Docs: `ARCHITECTURE.md`, `FEATURES.md`, `IMPLEMENTATION_STATUS.md`, `PERMISSIONS.md`, `DEVICE_COMPATIBILITY.md`, `DEVELOPMENT_ROADMAP.md`, `TESTING.md`.
# ViNT-LAUNCHER
