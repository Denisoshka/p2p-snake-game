package d.zhdanov.ccfit.nsu.core.network.core.node

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import java.net.InetSocketAddress

interface NodePayloadT {
  fun handleEvent(
    event: SnakesProto.GameMessage.SteerMsg, seq: Long,
  ): Boolean
  
  fun observerDetached()
  fun observableDetached()
  fun shootContextState(
    state: SnakesProto.GameState.Builder,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?,
    
    )
  
  fun getNodeRole(
    node: ClusterNodeT<Node.MsgInfo>,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?,
  ) = when(node.nodeState) {
    Node.NodeState.Active  -> when(node.nodeId) {
      masterAddrId.second  -> NodeRole.MASTER
      deputyAddrId?.second -> NodeRole.DEPUTY
      else                 -> NodeRole.NORMAL
    }
    
    Node.NodeState.Passive -> NodeRole.VIEWER
    else                   -> null
  }
}
