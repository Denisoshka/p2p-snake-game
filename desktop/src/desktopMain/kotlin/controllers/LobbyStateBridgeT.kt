package d.zhdanov.ccfit.nsu.states

import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig

interface LobbyStateBridgeT {
  fun launchNewGame(config: InternalGameConfig)
  fun terminateApplication()
}
