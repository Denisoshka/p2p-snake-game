package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.node.NodePayloadT
import java.net.InetSocketAddress

object PlugObserver : NodePayloadT {
  override fun handleEvent(
    event: SnakesProto.GameMessage.SteerMsg, seq: Long
  ): Boolean {
    return false
  }
  
  override fun observerDetached() {
  }
  
  override fun observableDetached() {
  }
  
  override fun shootContextState(
    state: SnakesProto.GameState.Builder,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?,
  ) {
  }
}