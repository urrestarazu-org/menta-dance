package com.menta.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.menta.android.presentation.MainScreen
import com.menta.android.presentation.MainViewModel
import com.menta.android.presentation.theme.MentaDanceTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MentaDanceTheme {
                MainScreen(
                    uiState = viewModel.uiState,
                    onRefresh = viewModel::refresh,
                )
            }
        }
    }
}
