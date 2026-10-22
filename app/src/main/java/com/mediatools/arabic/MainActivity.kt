package com.mediatools.arabic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mediatools.arabic.ui.AppRoot
import com.mediatools.arabic.ui.MediaToolsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaToolsTheme {
                AppRoot()
            }
        }
    }
}
