package com.nexysa7.crypto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexysa7.crypto.ui.NexysA7App
import com.nexysa7.crypto.ui.theme.NexysA7Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NexysA7Application).container
        setContent {
            NexysA7Theme {
                NexysA7App(container = container)
            }
        }
    }
}
