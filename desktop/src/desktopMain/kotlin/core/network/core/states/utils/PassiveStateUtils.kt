package core.network.core.states.utils

import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.PassiveState
import java.net.InetSocketAddress

object PassiveStateUtils {
  fun createPassiveState(
    clusterNodesHolder: ClusterNodesHolder,
    stateHolder: NetworkStateHolder,
    destAddr: InetSocketAddress,
    internalGameConfig: InternalGameConfig,
    masterId: Int,
    playerId: Int
  ): PassiveState {
    val masterNode = ClusterNode(
      nodeState = Node.NodeState.Passive,
      nodeId = masterId,
      ipAddress = destAddr,
      clusterNodesHolder = clusterNodesHolder,
      name = ""
    )
    clusterNodesHolder.registerNode(masterNode)
    return PassiveState(
      nodeId = playerId,
      stateHolder = stateHolder,
      gameConfig = internalGameConfig,
      nodesHolder = clusterNodesHolder
    )
  }
}