package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.node.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import java.net.InetSocketAddress

object PlugObserver : NodePayloadT {
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
  }
}