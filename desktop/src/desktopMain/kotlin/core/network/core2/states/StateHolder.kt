package d.zhdanov.ccfit.nsu.core.network.core2.states

import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNode
import java.net.InetSocketAddress

interface StateHolder {
  val masterDeputy: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress,
    Int>?>?
  val nextSeqNum: Long
  suspend fun handleNodeTermination(node: ClusterNode)
  suspend fun handleNodeSwitchToPassive(node: ClusterNode)
  fun findNewDeputy(oldDeputy: ClusterNode): ClusterNode?
}