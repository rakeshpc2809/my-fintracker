package com.fintracker.portfolioos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fintracker.portfolioos.ui.MainScreen
import com.fintracker.portfolioos.ui.theme.PortfolioOsTheme

import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PortfolioOsTheme {
                MainScreen()
            }
        }
    }
}
