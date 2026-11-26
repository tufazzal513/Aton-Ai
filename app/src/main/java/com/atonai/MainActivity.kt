package com.atonai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atonai.data.prefs.Prefs
import com.atonai.ui.AtonNavHost
import com.atonai.ui.theme.AtonTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = appGraph
        setContent {
            // Defaults are shown until the first prefs emission arrives.
            val prefs by graph.prefs.flow.collectAsStateWithLifecycle(initialValue = Prefs())
            AtonTheme(mode = prefs.themeMode, dynamicColor = prefs.dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AtonNavHost()
                }
            }
        }
    }
}
