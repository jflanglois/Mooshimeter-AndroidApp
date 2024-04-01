package com.mooshim.mooshimeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides

@Inject
class MainScreen(private val analytics: Analytics) {
}

@Component
abstract class ApplicationComponent() {
    val AnalyticsImpl.bind: Analytics
        @Provides get() = this
}

class MainActivity : ComponentActivity() {
    lateinit var mainScreen: MainScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainScreen = ActivityComponent::class.create(applicationComponent).mainScreen
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    Text(text = "Home")
                    Button(onClick = { navController.navigate("profile") }) {
                        Text(text = "Profile")
                    }
                }
                
                composable("profile") {
                    Text(text = "Profile")
                }
            }
        }
    }
}
