package d.zhdanov.ccfit.nsu.view

import androidx.compose.runtime.*
import d.zhdanov.ccfit.nsu.controllers.ControllerState
import d.zhdanov.ccfit.nsu.view.screens.GameScreen
import d.zhdanov.ccfit.nsu.view.screens.LobbyScreen

object App {
  @Composable
  fun launch() {
    val curScreen by remember { mutableStateOf(ControllerState.Lobby) }
    when(curScreen) {
      ControllerState.Lobby -> LobbyScreen()
      ControllerState.Game -> GameScreen()
    }
  }
}