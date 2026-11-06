package com.bluemimicry.launcher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.data.FolderAppEntity
import com.bluemimicry.launcher.data.FolderEntity
import com.bluemimicry.launcher.ui.components.*
import com.bluemimicry.launcher.ui.theme.LocalPalette

/**
 * MODE A — Classic Blue Mimicry. Two pages that mirror the reference screenshots:
 * page 0: widgets (clock + wave, search bar, frequent apps, screen time); page 1: floating groups.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClassicHome(
    vm: LauncherViewModel,
    editing: Boolean,
    fonts: LauncherFonts,
    onOpenFolder: (FolderEntity) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(initialPage = 1) { 2 }
    val settings by vm.settings.collectAsState()
    val pal = LocalPalette.current

    Box(modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !editing) { page ->
            if (page == 0) WidgetsPage(vm, editing, fonts, onSearch) else GroupsPage(vm, editing, fonts, onOpenFolder)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 230.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(2) { i ->
                Box(Modifier.size(if (pager.currentPage == i) 8.dp else 6.dp).background(pal.text.copy(alpha = if (pager.currentPage == i) 0.7f else 0.25f), CircleShape))
            }
        }
    }
}

@Composable
private fun WidgetsPage(vm: LauncherViewModel, editing: Boolean, fonts: LauncherFonts, onSearch: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val apps by vm.visibleApps.collectAsState()
    val meta by vm.meta.collectAsState()
    val hosted by vm.widgets.collectAsState()
    val dens = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        com.bluemimicry.launcher.widgets.HostedWidgets(vm, editing, with(dens) { w.toPx() }, with(dens) { h.toPx() }, hosted)
        // recompute suggestions when launch counts change
        val frequent = remember(meta, apps) { vm.suggestions(6) }

        if (settings.showClockWidget) ClockWidget(
            Modifier.offset(w * 0.11f, h * 0.160f).size(w * 0.77f, h * 0.140f),
            fontFamily = fonts.clock, fontScale = settings.fontScale, animate = !settings.reducedMotion,
            radiusDp = settings.containerRadius + 10f,
        )
        if (settings.showSearchBar) WideSearchBar(onSearch, Modifier.offset(w * 0.064f, h * 0.332f).width(w * 0.69f))

        if (frequent.isNotEmpty()) {
            val fake = remember { FolderEntity(id = -1, name = "Frequent", x = 0f, y = 0f, w = 0.31f) }
            FolderCard(
                fake, frequent.take(4), frequent.take(3).map { it.key }.toSet(), vm, w * 0.31f, false, fonts.label, settings.fontScale,
                onOpen = {}, onMove = { _, _ -> },
                modifier = Modifier.offset(w * 0.44f, h * 0.395f),
            )
        }
        if (settings.showScreenTime) ScreenTimeWidget(vm.repo, Modifier.offset(w * 0.064f, h * 0.650f).width(w * 0.31f))
    }
}

@Composable
private fun GroupsPage(vm: LauncherViewModel, editing: Boolean, fonts: LauncherFonts, onOpenFolder: (FolderEntity) -> Unit) {
    val folders by vm.folders.collectAsState()
    val links by vm.folderApps.collectAsState()
    val apps by vm.visibleApps.collectAsState()
    val settings by vm.settings.collectAsState()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        val wPx = with(density) { w.toPx() }; val hPx = with(density) { h.toPx() }
        val byKey = remember(apps) { apps.associateBy { it.key } }
        folders.forEach { f ->
            val mine: List<FolderAppEntity> = links.filter { it.folderId == f.id }
            val members = mine.mapNotNull { byKey[it.key] }
            val big = mine.filter { it.big }.map { it.key }.toSet()
            FolderCard(
                f, members, big, vm, w * f.w, editing, fonts.label, settings.fontScale,
                onOpen = { onOpenFolder(f) },
                onMove = { dx, dy -> vm.moveFolder(f, f.x + dx / wPx, f.y + dy / hPx) },
                modifier = Modifier.offset(w * f.x, h * f.y),
            )
        }
    }
}

data class LauncherFonts(val clock: FontFamily, val label: FontFamily)
