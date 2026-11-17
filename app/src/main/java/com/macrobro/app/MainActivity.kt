package com.macrobro.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.macrobro.app.ui.Bg
import com.macrobro.app.ui.BroScreen
import com.macrobro.app.ui.Hairline
import com.macrobro.app.ui.Lime
import com.macrobro.app.ui.MacroBroTheme
import com.macrobro.app.ui.ProgressScreen
import com.macrobro.app.ui.SettingsScreen
import com.macrobro.app.ui.SnapScreen
import com.macrobro.app.ui.TextDim
import com.macrobro.app.ui.TextMain
import com.macrobro.app.ui.TodayScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent { MacroBroTheme { MacroBroApp() } }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Today", Icons.Rounded.Home),
    Tab("Snap", Icons.Rounded.CameraAlt),
    Tab("Gym Bro", Icons.Rounded.FitnessCenter),
    Tab("Progress", Icons.Rounded.Insights),
    Tab("Settings", Icons.Rounded.Settings),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MacroBroApp(vm: AppViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            Column {
                HorizontalDivider(color = Hairline)
                NavigationBar(containerColor = Bg, tonalElevation = 0.dp) {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = {
                                Text(
                                    t.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Lime.copy(alpha = 0.16f),
                                selectedIconColor = Lime,
                                selectedTextColor = TextMain,
                                unselectedIconColor = TextDim,
                                unselectedTextColor = TextDim,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
        ) {
            when (tab) {
                0 -> TodayScreen(vm, onSnap = { tab = 1 })
                1 -> SnapScreen(vm, onDone = { tab = 0 }, onOpenSettings = { tab = 4 })
                2 -> BroScreen(vm, onOpenSettings = { tab = 4 })
                3 -> ProgressScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}
