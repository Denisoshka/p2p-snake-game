package d.zhdanov.ccfit.nsu.core.game.states

import d.zhdanov.ccfit.nsu.core.game.GameController
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameState

interface GameStateBridge {
  fun launchNewGame()
  fun exitGame()
  fun submitGameState(gameState: GameState)
}