package com.seenslide.teacher

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.seenslide.teacher.core.locale.LocaleHelper
import com.seenslide.teacher.core.network.auth.IdentityBootstrapper
import com.seenslide.teacher.core.ui.theme.SeenSlideTheme
import com.seenslide.teacher.core.ui.theme.ThemeMode
import com.seenslide.teacher.core.ui.theme.ThemeStore
import com.seenslide.teacher.navigation.SeenSlideNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeStore: ThemeStore

    @Inject
    lateinit var identityBootstrapper: IdentityBootstrapper

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch { identityBootstrapper.ensureBootstrap() }
        setContent {
            val themeMode by themeStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            SeenSlideTheme(darkTheme = isDark) {
                SeenSlideNavHost()
            }
        }
    }
}
