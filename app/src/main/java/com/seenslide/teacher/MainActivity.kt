package com.seenslide.teacher

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.seenslide.teacher.core.locale.LocaleHelper
import com.seenslide.teacher.core.ui.theme.SeenSlideTheme
import com.seenslide.teacher.navigation.SeenSlideNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeenSlideTheme {
                SeenSlideNavHost()
            }
        }
    }
}
