# Device Compatibility

**Tested devices: none.** The table lists known OEM behaviour to verify, not results.

| Area | Expectation / risk | To verify on |
|---|---|---|
| Android 9 (API 28) | minSdk. Blur (Android 12+), RoleManager (10+), Settings panels (10+) degrade to settings intents | Any API 28 device |
| Xiaomi / Redmi / POCO (MIUI/HyperOS) | Aggressive background kill → notification listener may be disconnected; enable **Autostart** and set battery to *No restrictions*. Default-home dialog may be OEM-styled. Settings→Apps→Default apps→Home app also works | Redmi/POCO |
| Samsung (One UI) | "Put unused apps to sleep" can disable the listener; DND/rotation intents differ slightly; Good Lock may override icon packs | Galaxy A/S |
| Tecno / Infinix (HiOS/XOS) | Similar background restrictions to MIUI; some OEM recorders have different package names (screen-record tile may say "none found") | Tecno/Infinix |
| Pixel | Reference behaviour; Live wallpaper picker & Panels API fully supported | Pixel |
| GPU | GL ES 2.0 minimum (declared in manifest). 3D uses one 1024² texture, ≤64 quads; Lightweight caps 30 fps | Low-RAM devices |
| Refresh rates | FPS cap per profile (30/60/90/120); loops use Choreographer frame clock | 90/120 Hz panels |
| Navigation | Gesture & 3-button both work; edge-to-edge insets handled with `statusBarsPadding`/`navigationBarsPadding`. Bottom-edge swipe-up zone may collide with a gesture-nav home swipe — assign carefully | Gesture-nav device |
| Low memory | Icon cache = 1/16 heap; shape app caps; reduced-motion option | ≤3 GB RAM |
| Package visibility | `<queries>` for launcher + icon-pack intents; no QUERY_ALL_PACKAGES | Android 11+ |
