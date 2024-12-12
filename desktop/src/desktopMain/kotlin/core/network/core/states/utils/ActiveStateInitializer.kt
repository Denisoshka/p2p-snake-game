package core.network.core.states.utils

import core.network.core.connection.Node
import core.network.core.connection.game.impl.ClusterNode
import core.network.core.connection.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.ActiveState
import java.net.InetSocketAddress

object ActiveStateInitializer {
  fun prepareActiveState(
    clusterNodesHandler: ClusterNodesHandler,
    stateHolder: NetworkStateHolder,
    destAddr: InetSocketAddress,
    internalGameConfig: InternalGameConfig,
    masterId: Int,
    playerId: Int
  ): ActiveState {
    val masterNode = ClusterNode(
      nodeState = Node.NodeState.Listener,
      nodeId = masterId,
      ipAddress = destAddr,
      clusterNodesHandler = clusterNodesHandler,
      name = ""
    )
    clusterNodesHandler.registerNode(masterNode)
    return ActiveState(
      gameConfig = internalGameConfig,
      stateHolder = stateHolder,
      clusterNodesHandler = clusterNodesHandler,
      nodeId = playerId
    )
  }
}
