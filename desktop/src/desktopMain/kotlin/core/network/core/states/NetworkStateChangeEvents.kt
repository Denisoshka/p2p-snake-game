package d.zhdanov.ccfit.nsu.core.network.core.states

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig

sealed class NetworkStateChangeEvents {
  data object SwitchToLobby : NetworkStateChangeEvents()
  data object MasterNow : NetworkStateChangeEvents()
  class JoinAsNormal(val config: GameConfig) : NetworkStateChangeEvents()
  class JoinAsViewer(val config: GameConfig) : NetworkStateChangeEvents()
  class LaunchGame(val config: GameConfig) : NetworkStateChangeEvents()
}