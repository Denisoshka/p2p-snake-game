package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import core.network.core.connection.Node
import core.network.core.connection.game.impl.ClusterNode
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import java.net.InetSocketAddress

interface NodePayloadT {
//  val name: String
  val score: Int
  val node: ClusterNode
  fun handleEvent(event: SteerMsg, seq: Long)
  fun observerTerminated()
  fun shootContextState(
    state: SnakesProto.GameState.Builder,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?
  )
  
  fun getNodeRole(
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?
  ) = when(node.nodeId) {
    masterAddrId.second  -> NodeRole.MASTER
    deputyAddrId?.second -> NodeRole.DEPUTY
    else                 -> {
      when(node.nodeState) {
        Node.NodeState.Passive -> NodeRole.NORMAL
        Node.NodeState.Active  -> NodeRole.VIEWER
        else                    -> null
      }
    }
  }
}