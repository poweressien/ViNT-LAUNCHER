package com.bluemimicry.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.bluemimicry.launcher.onboarding.Onboarding
import com.bluemimicry.launcher.ui.home.HomeShell
import com.bluemimicry.launcher.ui.theme.BmTheme
import com.bluemimicry.launcher.widgets.LocalWidgetController
import com.bluemimicry.launcher.widgets.WidgetController

/**
 * The HOME activity. singleTask + HOME/DEFAULT categories make it selectable through Android's
 * official default-home flow. All UI lives in composables; this class only wires lifecycle.
 */
class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private lateinit var widgets: WidgetController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Lowest-priority back callback (added first): a launcher has nothing to go "back" to, so it
        // swallows back at the root. Compose BackHandlers added later take priority while enabled.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) { override fun handleOnBackPressed() {} })
        widgets = WidgetController(this) { id, info ->
            val dm = resources.displayMetrics
            vm.placeWidget(id, (info.minWidth / dm.density).toInt(), (info.minHeight / dm.density).toInt(), dm.widthPixels / dm.density, dm.heightPixels / dm.density)
        }
        setContent {
            val s by vm.settings.collectAsState()
            // Supported status-bar styling: transparent bars with light/dark icons chosen per theme.
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = s.statusBarDark
                    isAppearanceLightNavigationBars = s.statusBarDark
                }
            }
            BmTheme(s.theme, Color(s.gradientTop).takeIf { s.theme == com.bluemimicry.launcher.data.ThemeId.BLUE_MIMICRY }, Color(s.gradientBottom).takeIf { s.theme == com.bluemimicry.launcher.data.ThemeId.BLUE_MIMICRY }, s.shadowIntensity) {
                CompositionLocalProvider(LocalWidgetController provides widgets) {
                    var showOnboarding by remember(s.onboarded) { mutableStateOf(!s.onboarded) }
                    if (showOnboarding) Onboarding(vm) { vm.update { it.copy(onboarded = true) }; showOnboarding = false } else HomeShell(vm)
                }
            }
        }
    }

    override fun onStart() { super.onStart(); widgets.start(); vm.refresh() }
    override fun onStop() { widgets.stop(); super.onStop() }

    /** Home pressed while we are already running (singleTask): return to a clean home page. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.homeEvents.tryEmit(Unit)
    }

    @Deprecated("Required by AppWidgetHost's activity-result flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        widgets.handleResult(requestCode, resultCode)
    }

}
