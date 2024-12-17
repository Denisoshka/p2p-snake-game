package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import java.net.InetSocketAddress

sealed interface Context {
  val state: Node.NodeState
  val score: Int
  fun handleEvent(
    event: SnakesProto.GameMessage.SteerMsg, seq: Long
  ): Boolean
  
  fun getEvent(): SnakesProto.GameMessage.SteerMsg?
  
  fun shootContextState(
    state: SnakesProto.GameState.Builder,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?,
  )
  
  interface Plug : Context
  
  interface Active : Context
  
  interface Passive : Context
}