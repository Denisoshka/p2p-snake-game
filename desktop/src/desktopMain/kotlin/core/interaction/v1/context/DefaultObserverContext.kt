package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.node.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import java.net.InetSocketAddress

object DefaultObserverContext : NodePayloadT {
  override fun handleEvent(
    event: SteerMsg,
    seq: Long,
    node: ClusterNode?
  ): Boolean {
    return false
  }
  
  override fun observerDetached(node: ClusterNode?) {
  }
  
  override fun observableDetached(node: ClusterNode?) {
  }
  
  override fun shootContextState(
    state: SnakesProto.GameState.Builder,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?,
    node: ClusterNode?
  ) {
    node ?: return
    val nodeRole = getNodeRole(node, masterAddrId, deputyAddrId) ?: return
    val pl = GamePlayer(
      name = node.name,
      id = node.nodeId,
      ipAddress = node.ipAddress.address.hostAddress,
      port = node.ipAddress.port,
      nodeRole = nodeRole,
      playerType = PlayerType.HUMAN,
      score = 0,
    )
    state.players.add(pl)
  }
}