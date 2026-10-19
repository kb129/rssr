package com.kb129.rssr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kb129.rssr.ui.RssrApp
import com.kb129.rssr.ui.theme.RssrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RssrTheme {
                RssrApp()
            }
        }
    }
}
