package d.zhdanov.ccfit.nsu.view

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import d.zhdanov.ccfit.nsu.controllers.ControllerState
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.view.screens.GameScreen
import d.zhdanov.ccfit.nsu.view.screens.LobbyScreen

@Composable
fun AppContent(gameController: GameController) {
  MaterialTheme {
    when (val currentScreen = gameController.currentScreen) {
      is ControllerState.Lobby -> LobbyScreen(
        announcements = gameController.announcementMsgsState,
        onStartGame = { config -> gameController.openGameScreen(config) },
        onRemoveOldMessages = { gameController.startMessageCleanup(60) }
      )
      ControllerState.Game -> GameScreen(
        gameConfig = currentScreen.gameConfig,
        onBackToLobby = { gameController.openLobby() }
      )
    }
  }
}