package core.network.core.states.utils

import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.network.core2.states.impl.state.PassiveStateImpl
import java.net.InetSocketAddress

object PassiveStateUtils {
  fun createPassiveState(
    clusterNodesHolder: ClusterNodesHolder,
    stateHolder: NetworkStateHolder,
    destAddr: InetSocketAddress,
    internalGameConfig: InternalGameConfig,
    masterId: Int,
    playerId: Int
  ): PassiveStateImpl {
    val masterNode = ClusterNode(
      nodeState = Node.NodeState.Passive,
      nodeId = masterId,
      ipAddress = destAddr,
      clusterNodesHolder = clusterNodesHolder,
      name = ""
    )
    clusterNodesHolder.registerNode(masterNode)
    return PassiveStateImpl(
      nodeId = playerId,
      stateHolder = stateHolder,
      gameConfig = internalGameConfig,
      nodesHolder = clusterNodesHolder
    )
  }
}