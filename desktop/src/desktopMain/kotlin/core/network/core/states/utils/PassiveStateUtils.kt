package core.network.core.states.utils

import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.PassiveState
import java.net.InetSocketAddress

object PassiveStateUtils {
  fun createPassiveState(
    clusterNodesHandler: ClusterNodesHandler,
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
      clusterNodesHolder = clusterNodesHandler,
      name = ""
    )
    clusterNodesHandler.registerNode(masterNode)
    return PassiveState(
      nodeId = playerId,
      stateHolder = stateHolder,
      gameConfig = internalGameConfig,
      clusterNodesHandler = clusterNodesHandler
    )
  }
}