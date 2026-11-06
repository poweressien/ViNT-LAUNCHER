# Testing

## Results so far
- **Compile:** not run (no Android SDK / Maven access in the authoring sandbox).
- **Unit / instrumented tests:** none written.
- **Static review performed:** brace/parenthesis balance on every Kotlin file; manual API/import review; known fixes applied
  (wrong Compose import, generic inference, per-frame recomposition, back-handling).

## Manual test plan (do this on a device)
**Foundation:** set default launcher; home button returns to clean home; back never exits; reboot keeps layout; install/uninstall an app → appears/disappears.
**Visual:** compare Classic page 1 & 2 with the reference screenshots (clock+wave, search bar, screen time, groups, badges, dock).
**Apps:** launch each dock app; long-press → rename/pin/hide(restore from drawer "Hidden")/change icon/move/uninstall/app info.
**Engines:** Floating (drag, pin, pause, speed); Shuffle (4 s swap, pinned stays, tap during motion does nothing); Orbital (reverse); 3D (each shape: rotate, pinch, tap-launch,
glass/metal/bg); each pseudo-3D mode; switch modes 20× quickly (no crash, no leak).
**Studio:** every tab changes the live home; presets save/apply; font import; wallpaper image/video; system wallpaper apply.
**System:** grant Notification Access → badges + island + centre; dismiss/open actions; privacy hide; Control Center tiles; lock gesture after enabling the service;
lock companion; weather opt-in; SMS permissions, send, receive (as default app).
**Perf:** Lightweight vs High on a low-end device; reduced motion; screen-off battery drain over 1 h with each engine idle.
