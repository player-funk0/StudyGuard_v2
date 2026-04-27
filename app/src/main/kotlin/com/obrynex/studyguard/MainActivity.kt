package com.obrynex.studyguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.obrynex.studyguard.navigation.NavGraph
import com.obrynex.studyguard.ui.theme.StudyGuardTheme

/**
 * Single-Activity host for the Compose navigation graph.
 *
 * All screen routing is handled by [AppNavGraph]; this class only
 * sets up the window and hands off to Compose.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StudyGuardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph()
                }
            }
        }
    }
}
