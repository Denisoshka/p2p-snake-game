package d.zhdanov.ccfit.nsu.core.network.core.states.events

import d.zhdanov.ccfit.nsu.controllers.dto.GameAnnouncement
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import java.net.InetSocketAddress

sealed class StateEvents {
  sealed class ControllerEvent : StateEvents() {
    data object SwitchToLobby : ControllerEvent()

    data class Join(
      val gameAnnouncement: GameAnnouncement,
      val playerName: String,
      val ipAddress: InetSocketAddress,
      val playerType: PlayerType
    ) : ControllerEvent()

    data class LaunchGame(val playerName: String, val gameConfig: GameConfig) :
      ControllerEvent()
  }

  sealed class InternalEvent : StateEvents() {
    data class JoinAck(
      val event: ControllerEvent.Join,
      val playerInfo: GamePlayer,
    ) : InternalEvent()

    data class MasterNow(
      val playerInfo: GamePlayer,
      val gameState: StateMsg,
      val gameConfig : GameConfig,
    ) : InternalEvent()
  }
}