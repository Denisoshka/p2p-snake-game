package d.zhdanov.ccfit.nsu.core.network.core.node

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import java.net.InetSocketAddress

interface NodePayloadT {
  fun handleEvent(
    event: SnakesProto.GameMessage.SteerMsg, seq: Long, node: ClusterNode? = null
  ): Boolean
  
  fun observerDetached(node: ClusterNode? = null)
  fun observableDetached(node: ClusterNode? = null)
  fun shootContextState(
    state: SnakesProto.GameState.Builder,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?,
    node: ClusterNode? = null
  )
  
  fun getNodeRole(
    node: ClusterNode,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?,
  ) = when(node.nodeId) {
    masterAddrId.second  -> NodeRole.MASTER
    deputyAddrId?.second -> NodeRole.DEPUTY
    else                 -> {
      when(node.nodeState) {
        Node.NodeState.Passive -> NodeRole.NORMAL
        Node.NodeState.Active  -> NodeRole.VIEWER
        else                   -> null
      }
    }
  }
}