package d.zhdanov.ccfit.nsu.core.network.node.connected

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node

sealed class ContextEvent {
  sealed class Controller : ContextEvent() {
    data class LaunchGame(
      val internalGameConfig: InternalGameConfig,
    ) : Controller()
    
    data object ExitGame : Controller()
  }
  
  sealed class Internal : ContextEvent() {
    data class DeputyNow(val candidate: ClusterNodeT<Node.MsgInfo>) : Internal()
    
    data class NewState(val state: SnakesProto.GameState) : Internal()
  }
}