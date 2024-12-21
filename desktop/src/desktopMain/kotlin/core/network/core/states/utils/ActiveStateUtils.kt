package core.network.core.states.utils

import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.network.core2.states.impl.state.ActiveStateImpl
import java.net.InetSocketAddress

object ActiveStateUtils {
  fun prepareActiveState(
    clusterNodesHolder: ClusterNodesHolder,
    stateHolder: NetworkStateHolder,
    destAddr: InetSocketAddress,
    internalGameConfig: InternalGameConfig,
    masterId: Int,
    playerId: Int
  ): ActiveStateImpl {
    val masterNode = ClusterNode(
      nodeState = Node.NodeState.Passive,
      nodeId = masterId,
      ipAddress = destAddr,
      clusterNodesHolder = clusterNodesHolder,
      name = ""
    )
    clusterNodesHolder.registerNode(masterNode)
    return ActiveStateImpl(
      internalGameConfig = internalGameConfig,
      stateHolder = stateHolder,
      nodesHolder = clusterNodesHolder,
      nodeId = playerId
    )
  }
}
