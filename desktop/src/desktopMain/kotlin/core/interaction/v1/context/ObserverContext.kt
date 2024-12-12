package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import core.network.core.connection.game.impl.ClusterNode
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import java.net.InetSocketAddress

open class ObserverContext(
  override val node: ClusterNode,
) : NodePayloadT {
  override val score: Int
    get() = 0
  
  override fun handleEvent(event: SteerMsg, seq: Long) {}
  
  override fun onContextObserverTerminated() {}
  
  override fun shootContextState(
    state: SnakesProto.GameState.Builder,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?
  ) {
    
    val nodeRole = getNodeRole(masterAddrId, deputyAddrId) ?: return
    val pl = GamePlayer(
      node.name,
      node.nodeId,
      node.ipAddress.address.hostAddress,
      node.ipAddress.port,
      nodeRole,
      PlayerType.HUMAN,
      score,
    )
    state.players.add(pl)
  }
}