package d.zhdanov.ccfit.nsu.core.network.core.states.events

import d.zhdanov.ccfit.nsu.controllers.dto.GameAnnouncement
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import java.net.InetSocketAddress

sealed class StateEvent {
  sealed class ControllerEvent : StateEvent() {
    data object SwitchToLobby : ControllerEvent()

    data class Join(
      val gameAnnouncement: GameAnnouncement,
      val playerName: String,
      val ipAddress: InetSocketAddress,
      val playerType: PlayerType
    ) : ControllerEvent()

    data class LaunchGame(
      val gameConfig: InternalGameConfig,
    ) :
      ControllerEvent()
  }

  sealed class InternalEvent : StateEvent() {
    data class JoinAck(
      val event: ControllerEvent.Join,
      val playerInfo: GamePlayer,
      val gameConfig: InternalGameConfig,
    ) : InternalEvent()

    data class MasterNow(
      val playerInfo: GamePlayer,
      val gameState: StateMsg,
      val gameConfig: InternalGameConfig,
    ) : InternalEvent()
  }
}