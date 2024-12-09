package d.zhdanov.ccfit.nsu.core.network.core.states.events

import d.zhdanov.ccfit.nsu.controllers.dto.GameAnnouncement
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

sealed class StateEvent {
  sealed class ControllerEvent : StateEvent() {
    data object SwitchToLobby : ControllerEvent()
    
    data class JoinReq(
      val gameAnnouncement: GameAnnouncement,
      val playerName: String,
      val playerRole : NodeRole,
      val playerType: PlayerType
    ) : ControllerEvent()
    
    data class LaunchGame(
      val internalGameConfig: InternalGameConfig,
    ) : ControllerEvent()
  }
  
  sealed class InternalEvent : StateEvent() {
    data class JoinReqAck(
      val onEventAck: ControllerEvent.JoinReq,
      val senderId: Int,
      val gamePlayerInfo: GamePlayerInfo,
      val internalGameConfig: InternalGameConfig,
    ) : InternalEvent()
    
    data class MasterNow(
      val gamePlayerInfo: GamePlayerInfo,
      val gameState: StateMsg,
      val internalGameConfig: InternalGameConfig,
    ) : InternalEvent()
  }
}