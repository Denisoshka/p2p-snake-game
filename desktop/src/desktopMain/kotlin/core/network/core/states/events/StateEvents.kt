package d.zhdanov.ccfit.nsu.core.network.core.states.events

import d.zhdanov.ccfit.nsu.controllers.dto.GameAnnouncement
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import java.net.InetSocketAddress

sealed class StateEvents {
  sealed class ControllerEvent : StateEvents() {
    data object SwitchToLobby : ControllerEvent()

    data class JoinAsNormal(
      val gameAnnouncement: GameAnnouncement,
      val playerName: String,
      val ipAddress: InetSocketAddress
    ) : ControllerEvent()

    data class JoinAsViewer(
      val gameAnnouncement: GameAnnouncement,
      val playerName: String,
      val ipAddress: InetSocketAddress
    ) : ControllerEvent()

    data class LaunchGame(val playerName: String, val gameConfig: GameConfig) :
      ControllerEvent()
  }

  sealed class InternalEvent : StateEvents() {
    data class JoinAsNormalAck(
      val event: ControllerEvent.JoinAsNormal,
      val playerInfo: GamePlayer,
    ) : InternalEvent()

    data class JoinAsViewerAck(
      val event: ControllerEvent.JoinAsViewer,
      val playerInfo: GamePlayer,
    ) : InternalEvent()

    data class MasterNow(
      val playerInfo: GamePlayer,
      val gameState: StateMsg,
    ) : InternalEvent()
  }
}