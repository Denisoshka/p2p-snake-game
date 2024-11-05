package d.zhdanov.ccfit.nsu.core.game.states

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

interface GameStateBridgeT {
  fun launchNewGame()
  fun exitGame()
  fun submitGameState(state: StateMsg)
}