package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.network.core.states.abstr.GameActor
import d.zhdanov.ccfit.nsu.core.network.core.states.abstr.NodeState
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import java.net.InetSocketAddress

class PassiveState(
  val nodeId: Int,
  val gameConfig: InternalGameConfig,
  private val stateHolder: NetworkStateHolder,
  private val clusterNodesHandler: ClusterNodesHandler,
) : NodeState.PassiveStateT, GameActor {
  override fun joinHandle(
    ipAddress: InetSocketAddress, message: GameMessage
  ) {
  }
  
  
  override fun pingHandle(
    ipAddress: InetSocketAddress, message: GameMessage
  ) {
  }
  
  override fun ackHandle(
    ipAddress: InetSocketAddress, message: GameMessage
  ) {
  }
  
  override fun stateHandle(
    ipAddress: InetSocketAddress, message: GameMessage
  ) {
  }
  
  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: GameMessage
  ) {
  }
  
  override fun announcementHandle(
    ipAddress: InetSocketAddress, message: GameMessage
  ) {
  }
  
  override fun errorHandle(ipAddress: InetSocketAddress, message: GameMessage) {
  }
  
  override fun steerHandle(
    ipAddress: InetSocketAddress, message: GameMessage
  ) {
  }
  
  fun cleanup() {
    clusterNodesHandler.shutdown()
  }
  
  override fun toLobby(
    event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
  ): NodeState {
  }
  
  override fun atNodeDetachPostProcess(
    node: ClusterNodeT<Node.MsgInfo>,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>?,
    accessToken: Any
  ) {
    /**
     * Я хз просто лишлняя перестраховка что не реагировать на  рандомные ноды
     * */
    if(node.nodeId == msInfo.second && dpInfo == null) {
      toLobby(Event.State.ByController.SwitchToLobby, accessToken)
    }
  }
  
}