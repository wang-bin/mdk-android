package com.mediadevkit.mdkplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.mediadevkit.sdk.KotlinPlayer

@Stable
object Navigator {
  var currentScreen: Screen by mutableStateOf(Screen.Root)
}

@Immutable
sealed interface Screen {
  data object Root : Screen
  data class Player(val url: String, val config: KotlinPlayer.Config) : Screen
}


class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { App() }
  }

}

@Composable
fun App() {
  MaterialTheme(
    colorScheme = darkColorScheme(),
    content = {
      Scaffold(
        content = {
          BackHandler(
            enabled = Navigator.currentScreen != Screen.Root,
            onBack = { Navigator.currentScreen = Screen.Root },
          )
          val modifier = Modifier.padding(it)
          when (val screen = Navigator.currentScreen) {
            is Screen.Player -> PlayerScreen(modifier, screen.url, screen.config)
            Screen.Root -> RootScreen(modifier)
          }
        }
      )
    }
  )
}



