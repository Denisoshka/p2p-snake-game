package d.zhdanov.ccfit.nsu.core.network.core.states.abstr

import d.zhdanov.ccfit.nsu.SnakesProto
import java.net.InetSocketAddress

interface AbstractStateHolder {
  
  val networkState: NodeState
  val gameState: SnakesProto.GameState?
  val masterDeputy: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?
}