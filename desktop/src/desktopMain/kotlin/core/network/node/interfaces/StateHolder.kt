package core.network.node.interfaces

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.states.abstr.NodeState
import java.net.InetSocketAddress

interface StateHolder {
  
  val networkState: NodeState
  val gameState: SnakesProto.GameState?
  val masterDeputy: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?
}