package com.bluemimicry.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Single source of truth for installed applications. Every display engine reads from here,
 * so switching engines never requires re-adding anything.
 */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val pm = context.packageManager

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    // Memory-bounded icon cache (1/16 of the heap by default).
    private val iconCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16L).toInt().coerceAtLeast(4 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val customIcons = HashMap<String, Bitmap>()

    /** Bumped whenever icons change (pack / custom replacement) so UI caches rebuild. */
    val iconVersion = MutableStateFlow(0)

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val self = context.packageName
        val list = launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != self }
            .map { info ->
                val ai = info.applicationInfo
                AppInfo(
                    pkg = info.componentName.packageName,
                    activity = info.componentName.className,
                    label = info.label?.toString() ?: info.componentName.packageName,
                    installTime = info.firstInstallTime,
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    category = ai.category,
                    user = info.user,
                )
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
        _apps.value = list
    }

    fun find(key: String): AppInfo? = _apps.value.firstOrNull { it.key == key }

    /** Real icon, rasterised once per size bucket and cached. Custom replacements win. */
    fun icon(app: AppInfo, sizePx: Int = 192): ImageBitmap {
        customIcons[app.key]?.let { return it.asImageBitmap() }
        val ck = "${app.key}@$sizePx"
        iconCache.get(ck)?.let { return it.asImageBitmap() }
        packIcon(app)?.let { d ->
            val b = d.toBitmap(sizePx, sizePx); iconCache.put(ck, b); return b.asImageBitmap()
        }
        val drawable = try {
            launcherApps.getActivityList(app.pkg, app.user)
                .firstOrNull { it.componentName.className == app.activity }
                ?.getIcon(0)
        } catch (_: Exception) { null } ?: pm.defaultActivityIcon
        val bmp = drawable.toBitmap(sizePx, sizePx)
        iconCache.put(ck, bmp)
        return bmp.asImageBitmap()
    }

    fun setCustomIcon(key: String, bitmap: Bitmap?) {
        if (bitmap == null) customIcons.remove(key) else customIcons[key] = bitmap
    }

    // ---- icon packs (ADW/Nova-style appfilter.xml) ------------------------------------------
    private var packRes: android.content.res.Resources? = null
    private var packPkg: String? = null
    private var packMap: Map<String, String> = emptyMap()

    /** Installed icon packs: packages that declare one of the standard icon-pack intents. */
    fun installedIconPacks(): List<Pair<String, String>> {
        val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.anddoes.launcher.THEME", "com.gau.go.launcherex.theme")
        val out = LinkedHashMap<String, String>()
        for (a in actions) pm.queryIntentActivities(Intent(a), 0).forEach {
            out[it.activityInfo.packageName] = it.loadLabel(pm).toString()
        }
        return out.map { it.key to it.value }
    }

    fun setIconPack(pkg: String?) {
        iconCache.evictAll()
        packPkg = pkg; packRes = null; packMap = emptyMap()
        iconVersion.value++
        if (pkg == null) return
        try {
            val res = pm.getResourcesForApplication(pkg); packRes = res
            val id = res.getIdentifier("appfilter", "xml", pkg)
            val parser = if (id != 0) res.getXml(id) else {
                // some packs ship appfilter.xml under assets
                val am = context.createPackageContext(pkg, 0).assets
                org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser().also { it.setInput(am.open("appfilter.xml"), null) }
            }
            val m = HashMap<String, String>()
            var ev = parser.eventType
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (ev == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component"); val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null) {
                        // "ComponentInfo{pkg/activity}"
                        val inner = comp.substringAfter("{", "").substringBefore("}")
                        if (inner.contains('/')) {
                            val p = inner.substringBefore('/')
                            val act = inner.substringAfter('/').let { if (it.startsWith(".")) p + it else it }
                            m["$p/$act"] = dr
                        }
                    }
                }
                ev = parser.next()
            }
            packMap = m
        } catch (_: Exception) { packPkg = null; packRes = null }
        iconVersion.value++
    }

    private fun packIcon(app: AppInfo): android.graphics.drawable.Drawable? {
        val res = packRes ?: return null; val p = packPkg ?: return null
        val name = packMap[app.key] ?: return null
        val id = res.getIdentifier(name, "drawable", p)
        return if (id != 0) try { androidx.core.content.res.ResourcesCompat.getDrawable(res, id, null) } catch (_: Exception) { null } else null
    }

    /** Re-apply per-app custom icons saved in app_meta (called once the apps and meta are known). */
    fun loadCustomIcons(paths: Map<String, String>) {
        customIcons.clear()
        paths.forEach { (key, path) -> android.graphics.BitmapFactory.decodeFile(path)?.let { customIcons[key] = it } }
        iconCache.evictAll()
        iconVersion.value++
    }

    fun launch(app: AppInfo): Boolean = try {
        launcherApps.startMainActivity(ComponentName(app.pkg, app.activity), app.user, null, null)
        true
    } catch (_: Exception) { false }

    fun openAppInfo(app: AppInfo) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }

    /** Android shows its own confirmation; system apps refuse and we simply report that. */
    fun uninstall(app: AppInfo) {
        val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }

    /** Static shortcuts/deep links the app publishes, when the launcher is the default home app. */
    fun shortcuts(app: AppInfo): List<android.content.pm.ShortcutInfo> = try {
        if (!launcherApps.hasShortcutHostPermission()) emptyList() else {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(app.pkg)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            launcherApps.getShortcuts(q, app.user).orEmpty()
        }
    } catch (_: Exception) { emptyList() }

    fun startShortcut(s: android.content.pm.ShortcutInfo) {
        runCatching { launcherApps.startShortcut(s, null, null) }
    }
}
