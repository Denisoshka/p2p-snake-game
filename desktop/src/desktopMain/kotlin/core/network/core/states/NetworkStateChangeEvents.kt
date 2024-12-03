package d.zhdanov.ccfit.nsu.core.network.core.states

import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

sealed class NetworkStateChangeEvents {
  data object SwitchToLobby : NetworkStateChangeEvents()

  data class MasterNow(val state: StateMsg, val config: InternalGameConfig) :
    NetworkStateChangeEvents()

  data class JoinAsNormal(val config: InternalGameConfig) :
    NetworkStateChangeEvents()

  data class JoinAsViewer(val config: InternalGameConfig) :
    NetworkStateChangeEvents()

  data class LaunchGame(val state: StateMsg, val config: InternalGameConfig) :
    NetworkStateChangeEvents()
}