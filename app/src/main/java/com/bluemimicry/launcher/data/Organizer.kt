package com.bluemimicry.launcher.data

import android.content.pm.ApplicationInfo

/**
 * First-run auto-organisation of REAL installed apps into the screenshot-style groups.
 * Only creates groups that actually have members; nothing is invented.
 */
object Organizer {

    private data class Def(val name: String, val x: Float, val y: Float, val w: Float)

    // Positions mirror the reference home screen (fractions of width/height).
    private val defs = listOf(
        Def("System apps", 0.25f, 0.140f, 0.31f),
        Def("Music & Audio", 0.63f, 0.065f, 0.31f),
        Def("Productivity", 0.63f, 0.235f, 0.31f),
        Def("Games", 0.06f, 0.325f, 0.31f),
        Def("Other", 0.06f, 0.490f, 0.31f),
        Def("Google", 0.63f, 0.490f, 0.31f),
        Def("Finance", 0.06f, 0.655f, 0.31f),
        Def("Social", 0.63f, 0.655f, 0.31f),
    )

    private val financeWords = listOf("bank", "pay", "wallet", "money", "finance", "cash", "trade", "invest", "zenith", "gtb", "opay", "kuda", "palm", "moniepoint")

    fun groupFor(app: AppInfo): String {
        val p = app.pkg.lowercase()
        val l = app.label.lowercase()
        return when {
            p.startsWith("com.google.") && !p.contains("gms") -> "Google"
            financeWords.any { p.contains(it) || l.contains(it) } -> "Finance"
            app.category == ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            app.category == ApplicationInfo.CATEGORY_AUDIO || app.category == ApplicationInfo.CATEGORY_VIDEO -> "Music & Audio"
            app.category == ApplicationInfo.CATEGORY_GAME -> "Games"
            app.category == ApplicationInfo.CATEGORY_PRODUCTIVITY || app.category == ApplicationInfo.CATEGORY_NEWS -> "Productivity"
            app.isSystem -> "System apps"
            else -> "Other"
        }
    }

    /** Default dock: dialer/messages/browser/camera-style system apps when present, else most recent installs. */
    fun defaultDock(apps: List<AppInfo>, slots: Int): List<String> {
        val wanted = listOf("dialer", "phone", "messag", "chrome", "camera", "gallery", "photos", "mail")
        val picked = LinkedHashSet<String>()
        for (w in wanted) {
            apps.firstOrNull { (it.pkg.contains(w) || it.label.lowercase().contains(w)) && it.key !in picked }
                ?.let { picked += it.key }
            if (picked.size >= slots) break
        }
        if (picked.size < slots) apps.sortedByDescending { it.installTime }.forEach {
            if (picked.size < slots) picked += it.key
        }
        return picked.toList()
    }

    suspend fun seed(apps: List<AppInfo>, dao: LayoutDao, dockSlots: Int) {
        if (apps.isEmpty() || dao.folderCount() > 0) return
        val dock = defaultDock(apps, dockSlots)
        dao.replaceDock(dock)
        val grouped = apps.filter { it.key !in dock }.groupBy(::groupFor)
        defs.forEachIndexed { idx, d ->
            val members = grouped[d.name].orEmpty().sortedBy { it.label.lowercase() }
            if (members.isEmpty()) return@forEachIndexed
            val id = dao.insertFolder(FolderEntity(name = d.name, x = d.x, y = d.y, w = d.w, sort = idx))
            dao.putFolderApps(members.mapIndexed { i, a -> FolderAppEntity(a.key, id, i, big = i < 6) })
        }
    }

    /** Apps installed after the first run land in a group automatically, without touching existing layout. */
    suspend fun placeNewcomers(
        apps: List<AppInfo>, placed: Set<String>, folders: List<FolderEntity>, dao: LayoutDao, dock: Set<String>,
    ) {
        val fresh = apps.filter { it.key !in placed && it.key !in dock }
        if (fresh.isEmpty() || folders.isEmpty()) return
        for (a in fresh) {
            val name = groupFor(a)
            val f = folders.firstOrNull { it.name == name } ?: folders.firstOrNull { it.name == "Other" } ?: folders.last()
            dao.putFolderApps(listOf(FolderAppEntity(a.key, f.id, 10_000 + (a.installTime % 1000).toInt(), big = false)))
        }
    }
}
