# Permissions

Nothing sensitive is requested at install. Each item is explained and requested only when its feature is used.
The launcher stays usable if all are denied.

| Permission / access | Why | How requested |
|---|---|---|
| Notification Access (special) | Real badges, island, notification centre | System settings screen, opened from onboarding/Studio |
| Usage Access `PACKAGE_USAGE_STATS` (special) | Screen-time widget | System settings screen |
| `READ_CONTACTS` | Contacts in search; names in SMS | Runtime prompt from the search / SMS screen |
| `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS` (+ MMS/WAP declared for the SMS role) | SMS module | Runtime prompt inside Messages |
| Default SMS role | Receive/store SMS, mark read | `RoleManager` dialog (Android 10+) |
| Default Home role | Be the launcher | `RoleManager` dialog / Settings |
| Accessibility service (optional) | Only `GLOBAL_ACTION_LOCK_SCREEN`; declares no events, no content access | User enables in Accessibility settings |
| Notification-policy access (optional) | Toggle Do Not Disturb | System settings, opened by the DND tile |
| `WRITE_SETTINGS` (optional) | Toggle auto-rotate | System settings, opened by the Rotation tile |
| `EXPAND_STATUS_BAR` (normal) | Open the system shade | Install-time (normal) |
| `SET_WALLPAPER` (normal) | Apply a system wallpaper when you ask | Install-time (normal) |
| `INTERNET` (normal) | **Only** opt-in weather (sends coordinates you typed to open-meteo.com) | Install-time; no request is made unless you configure weather |
| `POST_NOTIFICATIONS` | SMS notifications | Declared; used only when SMS role is held |
| `REQUEST_DELETE_PACKAGES` | Uninstall shortcut (Android shows its own confirmation) | Normal |

Not used: `QUERY_ALL_PACKAGES`, overlay (`SYSTEM_ALERT_WINDOW`), device admin, `BIND_*` beyond listed, background services.

**Play Store note:** SMS permissions and the accessibility service are policy-restricted on Google Play. For store release,
split the SMS module/lock service into a separate flavour or app. Sideloading/other stores are unaffected.
