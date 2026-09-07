package com.example.audioclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.example.audioclient.ui.AudioClientApp
import com.example.audioclient.ui.theme.AudioClientTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioClientTheme {
                Surface {
                    AudioClientApp()
                }
            }
        }
    }
}
