package core.network.core.states.utils

import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.ActiveState
import java.net.InetSocketAddress

object ActiveStateUtils {
  fun prepareActiveState(
    clusterNodesHandler: ClusterNodesHandler,
    stateHolder: NetworkStateHolder,
    destAddr: InetSocketAddress,
    internalGameConfig: InternalGameConfig,
    masterId: Int,
    playerId: Int
  ): ActiveState {
    val masterNode = ClusterNode(
      nodeState = Node.NodeState.Passive,
      nodeId = masterId,
      ipAddress = destAddr,
      clusterNodesHandler = clusterNodesHandler,
      name = ""
    )
    clusterNodesHandler.registerNode(masterNode)
    return ActiveState(
      internalGameConfig = internalGameConfig,
      stateHolder = stateHolder,
      nodesHolder = clusterNodesHandler,
      nodeId = playerId
    )
  }
}
