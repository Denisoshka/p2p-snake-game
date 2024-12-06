package d.zhdanov.ccfit.nsu.view

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.controllers.Screen
import d.zhdanov.ccfit.nsu.view.screens.GameScreen
import d.zhdanov.ccfit.nsu.view.screens.lobbyScreen

@Composable
fun app(gameController: GameController) {
  MaterialTheme {
    when(val currentScreen = gameController.currentScreen) {
      is Screen.Lobby -> {
        lobbyRoutine(gameController)
        lobbyScreen(
          announcements = gameController.announcementMsgsState,
          onStartGame = gameController::openGameScreen,
        )
      }

      is Screen.Game  -> GameScreen(
        gameConfig = currentScreen.gameConfig,
        onBackToLobby = gameController::openLobby
      )
    }
  }
}

@Composable
fun lobbyRoutine(gameController: GameController) {
  DisposableEffect(Unit) {
    gameController.startMessageCleanup(
      gameController.thresholdDelay, gameController.cleanupInterval
    )

    onDispose { gameController.stopMessageCleanup() }
  }
}