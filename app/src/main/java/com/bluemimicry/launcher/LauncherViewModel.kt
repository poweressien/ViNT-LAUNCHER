package com.bluemimicry.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bluemimicry.launcher.data.*
import com.bluemimicry.launcher.notifications.BadgeStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/** One shared view model: every display engine observes the same registry/meta/layout state. */
class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val la = app as LauncherApp
    val repo = la.apps
    private val db = la.db
    private val store = la.settings

    val settings: StateFlow<LauncherSettings> =
        store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    val allApps: StateFlow<List<AppInfo>> = repo.apps
    val meta: StateFlow<Map<String, AppMetaEntity>> = db.meta().all()
        .map { l -> l.associateBy { it.key } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val folders = db.layout().folders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val folderApps = db.layout().folderApps().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val dock = db.layout().dock().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val presets = db.presets().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val badges = BadgeStore.counts

    /** Apps visible in launcher views (hidden ones filtered out), with custom labels applied by [labelOf]. */
    val visibleApps: StateFlow<List<AppInfo>> = combine(allApps, meta) { a, m -> a.filter { m[it.key]?.hidden != true } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
        // apply per-app custom icons whenever meta changes
        viewModelScope.launch {
            meta.collect { m -> repo.loadCustomIcons(m.values.mapNotNull { e -> e.customIconPath?.let { e.key to it } }.toMap()) }
        }
        // apply the chosen icon pack once (and when it changes)
        viewModelScope.launch {
            store.flow.map { it.iconPack }.distinctUntilChanged().collect { repo.setIconPack(it) }
        }
    }

    fun setCustomIcon(app: AppInfo, uri: android.net.Uri?) = viewModelScope.launch {
        val path = uri?.let { Assets.import(getApplication<Application>(), it, "icons", app.key.hashCode().toString() + ".img") }
        db.meta().edit(app.key) { it.copy(customIconPath = path) }
    }

    fun importWallpaper(uri: android.net.Uri) = viewModelScope.launch {
        Assets.import(getApplication<Application>(), uri, "wallpaper", "bg_${System.currentTimeMillis()}.img")?.let { p ->
            store.update { it.copy(wallpaperPath = p, videoWallpaperPath = null) }
        }
    }

    fun importVideoWallpaper(uri: android.net.Uri) = viewModelScope.launch {
        Assets.import(getApplication<Application>(), uri, "wallpaper", "video_${System.currentTimeMillis()}.mp4")?.let { p ->
            store.update { it.copy(videoWallpaperPath = p) }
        }
    }

    fun importFont(uri: android.net.Uri) = viewModelScope.launch {
        Assets.import(getApplication<Application>(), uri, "fonts", "custom_${System.currentTimeMillis()}.ttf")?.let { p ->
            store.update { it.copy(customFontPath = p, clockFont = "Custom", labelFont = "Custom") }
        }
    }

    fun refresh() = viewModelScope.launch {
        repo.refresh()
        val apps = repo.apps.value
        Organizer.seed(apps, db.layout(), settings.value.dockSlots)
        val dao = db.layout()
        Organizer.placeNewcomers(apps, dao.placedKeys().toSet(), dao.foldersNow(), dao, dao.dockKeys().toSet())
    }

    fun labelOf(app: AppInfo): String = meta.value[app.key]?.customLabel?.takeIf { it.isNotBlank() } ?: app.label

    fun launch(app: AppInfo) {
        if (repo.launch(app)) viewModelScope.launch { db.meta().recordLaunch(app.key, System.currentTimeMillis()) }
    }

    fun update(block: (LauncherSettings) -> LauncherSettings) = viewModelScope.launch { store.update(block) }

    // ---- app management -------------------------------------------------
    fun rename(app: AppInfo, name: String?) = viewModelScope.launch { db.meta().edit(app.key) { it.copy(customLabel = name?.trim()) } }
    fun hide(app: AppInfo, hidden: Boolean) = viewModelScope.launch { db.meta().edit(app.key) { it.copy(hidden = hidden) } }
    fun pin(app: AppInfo, pinned: Boolean) = viewModelScope.launch { db.meta().edit(app.key) { it.copy(pinned = pinned) } }
    fun scale(app: AppInfo, s: Float) = viewModelScope.launch { db.meta().edit(app.key) { it.copy(iconSizeScale = s.coerceIn(0.6f, 1.6f)) } }

    // ---- layout ---------------------------------------------------------
    fun setDock(keys: List<String>) = viewModelScope.launch { db.layout().replaceDock(keys.take(settings.value.dockSlots)) }
    fun addToDock(app: AppInfo) = setDock((dock.value.map { it.key } + app.key).distinct())
    fun removeFromDock(key: String) = setDock(dock.value.map { it.key } - key)

    fun moveFolder(f: FolderEntity, x: Float, y: Float) =
        viewModelScope.launch { db.layout().updateFolder(f.copy(x = x.coerceIn(0f, 1f - f.w), y = y.coerceIn(0f, 0.9f))) }

    fun resizeFolder(f: FolderEntity, w: Float) =
        viewModelScope.launch { db.layout().updateFolder(f.copy(w = w.coerceIn(0.22f, 0.9f))) }

    fun renameFolder(f: FolderEntity, name: String) = viewModelScope.launch { db.layout().updateFolder(f.copy(name = name)) }

    fun createFolder(name: String) = viewModelScope.launch {
        db.layout().insertFolder(FolderEntity(name = name, x = 0.3f, y = 0.4f, w = 0.31f, sort = folders.value.size))
    }

    fun deleteFolder(f: FolderEntity) = viewModelScope.launch {
        // Members fall back to the drawer; nothing is uninstalled.
        db.layout().clearFolder(f.id); db.layout().deleteFolder(f)
    }

    fun moveToFolder(app: AppInfo, folder: FolderEntity) = viewModelScope.launch {
        db.layout().moveApp(app.key, folder.id, 0, big = true)
    }

    // ---- hosted widgets ---------------------------------------------------
    val widgets = db.widgets().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val homeEvents = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun placeWidget(id: Int, minWdp: Int, minHdp: Int, pageWdp: Float, pageHdp: Float) = viewModelScope.launch {
        val w = (minWdp.coerceAtLeast(110) / pageWdp).coerceIn(0.2f, 0.9f)
        val h = (minHdp.coerceAtLeast(110) / pageHdp).coerceIn(0.08f, 0.6f)
        db.widgets().put(WidgetEntity(id, 0.06f, 0.5f, w, h))
    }
    fun moveWidget(w: WidgetEntity, x: Float, y: Float) = viewModelScope.launch { db.widgets().put(w.copy(x = x.coerceIn(0f, 1f - w.w), y = y.coerceIn(0f, 1f - w.h))) }
    fun resizeWidget(w: WidgetEntity, f: Float) = viewModelScope.launch { db.widgets().put(w.copy(w = (w.w * f).coerceIn(0.15f, 0.95f), h = (w.h * f).coerceIn(0.06f, 0.7f))) }
    fun removeWidget(w: WidgetEntity) = viewModelScope.launch { db.widgets().delete(w) }

    // ---- presets --------------------------------------------------------
    fun savePreset(name: String) = viewModelScope.launch {
        val s = settings.value
        val j = JSONObject()
            .put("mode", s.mode.name).put("shape3d", s.shape3d.name).put("theme", s.theme.name)
            .put("iconStyle", s.iconStyle.name).put("floatSpeed", s.floatSpeed.toDouble())
            .put("floatIntensity", s.floatIntensity.toDouble()).put("shuffleSeconds", s.shuffleSeconds)
            .put("orbitSpeed", s.orbitSpeed.toDouble()).put("rotationSpeed", s.rotationSpeed.toDouble())
        db.presets().insert(PresetEntity(name = name, json = j.toString()))
    }

    fun applyPreset(p: PresetEntity) = update { s ->
        val j = JSONObject(p.json)
        s.copy(
            mode = runCatching { DisplayMode.valueOf(j.getString("mode")) }.getOrDefault(s.mode),
            shape3d = runCatching { Shape3D.valueOf(j.getString("shape3d")) }.getOrDefault(s.shape3d),
            theme = runCatching { ThemeId.valueOf(j.getString("theme")) }.getOrDefault(s.theme),
            iconStyle = runCatching { IconStyle.valueOf(j.getString("iconStyle")) }.getOrDefault(s.iconStyle),
            floatSpeed = j.optDouble("floatSpeed", s.floatSpeed.toDouble()).toFloat(),
            floatIntensity = j.optDouble("floatIntensity", s.floatIntensity.toDouble()).toFloat(),
            shuffleSeconds = j.optInt("shuffleSeconds", s.shuffleSeconds),
            orbitSpeed = j.optDouble("orbitSpeed", s.orbitSpeed.toDouble()).toFloat(),
            rotationSpeed = j.optDouble("rotationSpeed", s.rotationSpeed.toDouble()).toFloat(),
        )
    }

    fun deletePreset(p: PresetEntity) = viewModelScope.launch { db.presets().delete(p) }

    // ---- smart (on-device only) ----------------------------------------
    /** Frequency + recency score from the launcher's own launch log. Nothing leaves the device. */
    fun suggestions(limit: Int = 5): List<AppInfo> {
        val now = System.currentTimeMillis()
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return visibleApps.value
            .mapNotNull { a -> meta.value[a.key]?.takeIf { it.launchCount > 0 }?.let { a to it } }
            .sortedByDescending { (_, m) ->
                val ageDays = ((now - m.lastLaunch) / 86_400_000.0).coerceAtLeast(0.0)
                m.launchCount * Math.exp(-ageDays / 7.0)
            }
            .map { it.first }.take(limit)
    }
}
