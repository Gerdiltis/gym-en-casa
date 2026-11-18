package com.gymencasa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymencasa.ui.AppViewModel
import com.gymencasa.ui.navigation.GymNavRoot
import com.gymencasa.ui.theme.GymTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark by appViewModel.darkTheme.collectAsStateWithLifecycle()
            GymTheme(darkTheme = dark) {
                GymNavRoot(appViewModel)
            }
        }
    }
}
