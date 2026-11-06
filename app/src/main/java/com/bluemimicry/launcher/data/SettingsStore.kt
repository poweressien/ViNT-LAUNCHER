package com.bluemimicry.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore("launcher_settings")

enum class DisplayMode(val label: String) {
    CLASSIC("Classic Blue Mimicry"),
    FLOATING("Floating"),
    SHUFFLE("Auto Shuffle"),
    ORBITAL("Orbital"),
    SHAPES3D("3D Shapes"),
    HOLOGRAPHIC("Holographic"),
    GALAXY("Galaxy"),
    MATRIX("Matrix"),
    CAROUSEL("Carousel"),
    TIMELINE("Timeline"),
    MAGNETIC("Magnetic"),
    DNA("DNA"),
    LIQUID("Liquid"),
    ADAPTIVE("Adaptive"),
}

enum class Shape3D(val label: String) { CUBE("Cube"), SPHERE("Sphere"), CYLINDER("Cylinder"), RING("Ring"), PYRAMID("Pyramid"), CAROUSEL("Multi-layer carousel") }
enum class IconStyle(val label: String) { NEUMORPHIC("Neumorphic"), CIRCLE("Circular"), SQUIRCLE("Squircle"), ROUNDED("Rounded square"), SQUARE("Square"), MINIMAL("Minimal"), TRANSPARENT("Transparent"), GLASS("Glass"), RAISED3D("3D") }
enum class ThemeId(val label: String) { BLUE_MIMICRY("Blue Mimicry"), IOS("Premium iOS"), NEUMORPHISM("Pure Neumorphism"), MINIMAL_WHITE("Minimal White"), AMOLED("AMOLED Black"), FUTURISTIC("Futuristic Dark"), HOLOGRAPHIC("Holographic"), GLASS("Glass Interface"), COLORFUL("Colorful Dynamic") }
enum class PerfProfile(val label: String, val fpsCap: Int, val sphereDetail: Int) {
    LIGHTWEIGHT("Lightweight", 30, 12), BALANCED("Balanced", 60, 20), HIGH("High Quality", 90, 32), EXPERIMENTAL("Experimental", 120, 40)
}

data class LauncherSettings(
    val mode: DisplayMode = DisplayMode.CLASSIC,
    val theme: ThemeId = ThemeId.BLUE_MIMICRY,
    val iconStyle: IconStyle = IconStyle.NEUMORPHIC,
    val perf: PerfProfile = PerfProfile.BALANCED,
    val reducedMotion: Boolean = false,
    // layout
    val containerRadius: Float = 28f,
    val shadowIntensity: Float = 1f,
    val containerAlpha: Float = 0.85f,
    val iconSize: Float = 52f,
    val showLabels: Boolean = true,
    val dockAlpha: Float = 0f,
    val dockSlots: Int = 5,
    // widgets / top
    val showClockWidget: Boolean = true,
    val showSeconds: Boolean = true,
    val showSearchBar: Boolean = true,
    val showScreenTime: Boolean = true,
    val showIsland: Boolean = true,
    // engines
    val floatSpeed: Float = 1f,
    val floatIntensity: Float = 1f,
    val floatPaused: Boolean = false,
    val shuffleSeconds: Int = 4,
    val shuffleWholeScreen: Boolean = true,
    val shufflePaused: Boolean = false,
    val orbitSpeed: Float = 1f,
    val orbitReverse: Boolean = false,
    val shape3d: Shape3D = Shape3D.CUBE,
    val rotationSpeed: Float = 1f,
    val autoRotate: Boolean = true,
    val lightIntensity: Float = 1f,
    val shapeMetallic: Float = 0.3f,
    val shapeColor: Long = 0xFFDADCE2,
    val shapeGlass: Boolean = false,
    val bg3d: Int = 0,          // 0 light grey, 1 dark, 2 blue gradient
    val fov: Float = 50f,       // camera perspective
    // animations
    val animIntensity: Float = 1f,
    val animDurationMs: Int = 320,
    val spring: Boolean = true,
    // fonts
    val fontScale: Float = 1f,
    val clockFont: String = "Sans",
    val labelFont: String = "Sans",
    val customFontPath: String? = null,
    // wallpaper
    val wallpaperPath: String? = null,
    val wallpaperBlur: Float = 0f,
    val gradientTop: Long = 0xFFD6D6D6,
    val gradientBottom: Long = 0xFFC4C4C4,
    // gestures
    val gestureDoubleTap: String = "LOCK",
    val gestureSwipeUp: String = "DRAWER",
    val gestureSwipeDown: String = "NOTIFICATIONS",
    val gestureLongPress: String = "EDIT",
    val gestureShake: String = "SHUFFLE",
    val gesturePinch: String = "NEXT_MODE",
    val gestureTwoFinger: String = "CONTROL_CENTER",
    val smartSuggestions: Boolean = true,
    val gestureSwipeLeft: String = "NONE",
    val gestureSwipeRight: String = "NONE",
    val gestureTilt: String = "NONE",
    val hideNotifContent: Boolean = false,
    val onboarded: Boolean = false,
    val iconPack: String? = null,
    val statusBarDark: Boolean = true,
    val videoWallpaperPath: String? = null,
    val animatedBackground: Boolean = false,
)

class SettingsStore(private val context: Context) {
    val flow: Flow<LauncherSettings> = context.ds.data.map { p -> p.toSettings() }

    suspend fun update(block: (LauncherSettings) -> LauncherSettings) {
        context.ds.edit { prefs ->
            val next = block(prefs.toSettings())
            prefs.writeAll(next)
        }
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(k: String, d: E): E =
        this[stringPreferencesKey(k)]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: d

    private fun Preferences.toSettings(): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            mode = enum("mode", d.mode), theme = enum("theme", d.theme),
            iconStyle = enum("iconStyle", d.iconStyle), perf = enum("perf", d.perf),
            reducedMotion = this[booleanPreferencesKey("reducedMotion")] ?: d.reducedMotion,
            containerRadius = this[floatPreferencesKey("containerRadius")] ?: d.containerRadius,
            shadowIntensity = this[floatPreferencesKey("shadowIntensity")] ?: d.shadowIntensity,
            containerAlpha = this[floatPreferencesKey("containerAlpha")] ?: d.containerAlpha,
            iconSize = this[floatPreferencesKey("iconSize")] ?: d.iconSize,
            showLabels = this[booleanPreferencesKey("showLabels")] ?: d.showLabels,
            dockAlpha = this[floatPreferencesKey("dockAlpha")] ?: d.dockAlpha,
            dockSlots = this[intPreferencesKey("dockSlots")] ?: d.dockSlots,
            showClockWidget = this[booleanPreferencesKey("showClockWidget")] ?: d.showClockWidget,
            showSeconds = this[booleanPreferencesKey("showSeconds")] ?: d.showSeconds,
            showSearchBar = this[booleanPreferencesKey("showSearchBar")] ?: d.showSearchBar,
            showScreenTime = this[booleanPreferencesKey("showScreenTime")] ?: d.showScreenTime,
            showIsland = this[booleanPreferencesKey("showIsland")] ?: d.showIsland,
            floatSpeed = this[floatPreferencesKey("floatSpeed")] ?: d.floatSpeed,
            floatIntensity = this[floatPreferencesKey("floatIntensity")] ?: d.floatIntensity,
            floatPaused = this[booleanPreferencesKey("floatPaused")] ?: d.floatPaused,
            shuffleSeconds = this[intPreferencesKey("shuffleSeconds")] ?: d.shuffleSeconds,
            shuffleWholeScreen = this[booleanPreferencesKey("shuffleWholeScreen")] ?: d.shuffleWholeScreen,
            shufflePaused = this[booleanPreferencesKey("shufflePaused")] ?: d.shufflePaused,
            orbitSpeed = this[floatPreferencesKey("orbitSpeed")] ?: d.orbitSpeed,
            orbitReverse = this[booleanPreferencesKey("orbitReverse")] ?: d.orbitReverse,
            shape3d = enum("shape3d", d.shape3d),
            rotationSpeed = this[floatPreferencesKey("rotationSpeed")] ?: d.rotationSpeed,
            autoRotate = this[booleanPreferencesKey("autoRotate")] ?: d.autoRotate,
            lightIntensity = this[floatPreferencesKey("lightIntensity")] ?: d.lightIntensity,
            shapeMetallic = this[floatPreferencesKey("shapeMetallic")] ?: d.shapeMetallic,
            shapeColor = this[longPreferencesKey("shapeColor")] ?: d.shapeColor,
            shapeGlass = this[booleanPreferencesKey("shapeGlass")] ?: d.shapeGlass,
            bg3d = this[intPreferencesKey("bg3d")] ?: d.bg3d,
            fov = this[floatPreferencesKey("fov")] ?: d.fov,
            animIntensity = this[floatPreferencesKey("animIntensity")] ?: d.animIntensity,
            animDurationMs = this[intPreferencesKey("animDurationMs")] ?: d.animDurationMs,
            spring = this[booleanPreferencesKey("spring")] ?: d.spring,
            fontScale = this[floatPreferencesKey("fontScale")] ?: d.fontScale,
            clockFont = this[stringPreferencesKey("clockFont")] ?: d.clockFont,
            labelFont = this[stringPreferencesKey("labelFont")] ?: d.labelFont,
            customFontPath = this[stringPreferencesKey("customFontPath")],
            wallpaperPath = this[stringPreferencesKey("wallpaperPath")],
            wallpaperBlur = this[floatPreferencesKey("wallpaperBlur")] ?: d.wallpaperBlur,
            gradientTop = this[longPreferencesKey("gradientTop")] ?: d.gradientTop,
            gradientBottom = this[longPreferencesKey("gradientBottom")] ?: d.gradientBottom,
            gestureDoubleTap = this[stringPreferencesKey("gDouble")] ?: d.gestureDoubleTap,
            gestureSwipeUp = this[stringPreferencesKey("gUp")] ?: d.gestureSwipeUp,
            gestureSwipeDown = this[stringPreferencesKey("gDown")] ?: d.gestureSwipeDown,
            gestureLongPress = this[stringPreferencesKey("gLong")] ?: d.gestureLongPress,
            gestureShake = this[stringPreferencesKey("gShake")] ?: d.gestureShake,
            gesturePinch = this[stringPreferencesKey("gPinch")] ?: d.gesturePinch,
            gestureTwoFinger = this[stringPreferencesKey("gTwo")] ?: d.gestureTwoFinger,
            smartSuggestions = this[booleanPreferencesKey("smart")] ?: d.smartSuggestions,
            gestureSwipeLeft = this[stringPreferencesKey("gLeft")] ?: d.gestureSwipeLeft,
            gestureSwipeRight = this[stringPreferencesKey("gRight")] ?: d.gestureSwipeRight,
            gestureTilt = this[stringPreferencesKey("gTilt")] ?: d.gestureTilt,
            hideNotifContent = this[booleanPreferencesKey("hideNotif")] ?: d.hideNotifContent,
            onboarded = this[booleanPreferencesKey("onboarded")] ?: d.onboarded,
            iconPack = this[stringPreferencesKey("iconPack")],
            statusBarDark = this[booleanPreferencesKey("sbDark")] ?: d.statusBarDark,
            videoWallpaperPath = this[stringPreferencesKey("videoWp")],
            animatedBackground = this[booleanPreferencesKey("animBg")] ?: d.animatedBackground,
        )
    }

    private fun MutablePreferences.writeAll(s: LauncherSettings) {
        this[stringPreferencesKey("mode")] = s.mode.name
        this[stringPreferencesKey("theme")] = s.theme.name
        this[stringPreferencesKey("iconStyle")] = s.iconStyle.name
        this[stringPreferencesKey("perf")] = s.perf.name
        this[booleanPreferencesKey("reducedMotion")] = s.reducedMotion
        this[floatPreferencesKey("containerRadius")] = s.containerRadius
        this[floatPreferencesKey("shadowIntensity")] = s.shadowIntensity
        this[floatPreferencesKey("containerAlpha")] = s.containerAlpha
        this[floatPreferencesKey("iconSize")] = s.iconSize
        this[booleanPreferencesKey("showLabels")] = s.showLabels
        this[floatPreferencesKey("dockAlpha")] = s.dockAlpha
        this[intPreferencesKey("dockSlots")] = s.dockSlots
        this[booleanPreferencesKey("showClockWidget")] = s.showClockWidget
        this[booleanPreferencesKey("showSeconds")] = s.showSeconds
        this[booleanPreferencesKey("showSearchBar")] = s.showSearchBar
        this[booleanPreferencesKey("showScreenTime")] = s.showScreenTime
        this[booleanPreferencesKey("showIsland")] = s.showIsland
        this[floatPreferencesKey("floatSpeed")] = s.floatSpeed
        this[floatPreferencesKey("floatIntensity")] = s.floatIntensity
        this[booleanPreferencesKey("floatPaused")] = s.floatPaused
        this[intPreferencesKey("shuffleSeconds")] = s.shuffleSeconds
        this[booleanPreferencesKey("shuffleWholeScreen")] = s.shuffleWholeScreen
        this[booleanPreferencesKey("shufflePaused")] = s.shufflePaused
        this[floatPreferencesKey("orbitSpeed")] = s.orbitSpeed
        this[booleanPreferencesKey("orbitReverse")] = s.orbitReverse
        this[stringPreferencesKey("shape3d")] = s.shape3d.name
        this[floatPreferencesKey("rotationSpeed")] = s.rotationSpeed
        this[booleanPreferencesKey("autoRotate")] = s.autoRotate
        this[floatPreferencesKey("lightIntensity")] = s.lightIntensity
        this[floatPreferencesKey("shapeMetallic")] = s.shapeMetallic
        this[longPreferencesKey("shapeColor")] = s.shapeColor
        this[booleanPreferencesKey("shapeGlass")] = s.shapeGlass
        this[intPreferencesKey("bg3d")] = s.bg3d
        this[floatPreferencesKey("fov")] = s.fov
        this[floatPreferencesKey("animIntensity")] = s.animIntensity
        this[intPreferencesKey("animDurationMs")] = s.animDurationMs
        this[booleanPreferencesKey("spring")] = s.spring
        this[floatPreferencesKey("fontScale")] = s.fontScale
        this[stringPreferencesKey("clockFont")] = s.clockFont
        this[stringPreferencesKey("labelFont")] = s.labelFont
        s.customFontPath?.let { this[stringPreferencesKey("customFontPath")] = it } ?: remove(stringPreferencesKey("customFontPath"))
        s.wallpaperPath?.let { this[stringPreferencesKey("wallpaperPath")] = it } ?: remove(stringPreferencesKey("wallpaperPath"))
        this[floatPreferencesKey("wallpaperBlur")] = s.wallpaperBlur
        this[longPreferencesKey("gradientTop")] = s.gradientTop
        this[longPreferencesKey("gradientBottom")] = s.gradientBottom
        this[stringPreferencesKey("gDouble")] = s.gestureDoubleTap
        this[stringPreferencesKey("gUp")] = s.gestureSwipeUp
        this[stringPreferencesKey("gDown")] = s.gestureSwipeDown
        this[stringPreferencesKey("gLong")] = s.gestureLongPress
        this[stringPreferencesKey("gShake")] = s.gestureShake
        this[stringPreferencesKey("gPinch")] = s.gesturePinch
        this[stringPreferencesKey("gTwo")] = s.gestureTwoFinger
        this[booleanPreferencesKey("smart")] = s.smartSuggestions
        this[stringPreferencesKey("gLeft")] = s.gestureSwipeLeft
        this[stringPreferencesKey("gRight")] = s.gestureSwipeRight
        this[stringPreferencesKey("gTilt")] = s.gestureTilt
        this[booleanPreferencesKey("hideNotif")] = s.hideNotifContent
        this[booleanPreferencesKey("onboarded")] = s.onboarded
        s.iconPack?.let { this[stringPreferencesKey("iconPack")] = it } ?: remove(stringPreferencesKey("iconPack"))
        this[booleanPreferencesKey("sbDark")] = s.statusBarDark
        s.videoWallpaperPath?.let { this[stringPreferencesKey("videoWp")] = it } ?: remove(stringPreferencesKey("videoWp"))
        this[booleanPreferencesKey("animBg")] = s.animatedBackground
    }
}
