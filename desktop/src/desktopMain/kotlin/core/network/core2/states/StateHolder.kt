package d.zhdanov.ccfit.nsu.core.network.core2.states

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNodesHolder
import java.net.InetSocketAddress

interface StateHolder {
  val nodesHolder: ClusterNodesHolder
  val gameController: GameController
  val masterDeputy: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?
  val gameState: SnakesProto.GameState?
  val nextSeqNum: Long
  
  suspend fun handleNodeTermination(node: ClusterNode)
  suspend fun handleNodeSwitchToPassive(node: ClusterNode)
  fun findNewDeputy(oldDeputy: ClusterNode): ClusterNode?
  val networkState: NodeState
}