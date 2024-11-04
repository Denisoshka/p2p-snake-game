package d.zhdanov.ccfit.nsu.core.game.states

import d.zhdanov.ccfit.nsu.core.game.GameConfig

interface LobbyStateBridge {
  fun launchNewGame(config: GameConfig)
  fun terminateApplication()
}
