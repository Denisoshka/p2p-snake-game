package d.zhdanov.ccfit.nsu.states

import d.zhdanov.ccfit.nsu.core.game.GameConfig

interface LobbyStateBridgeT {
  fun launchNewGame(config: GameConfig)
  fun terminateApplication()
}
