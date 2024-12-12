package d.zhdanov.ccfit.nsu.core.network.core.states.initializers

import core.network.core.connection.Node
import core.network.core.connection.game.impl.ClusterNode
import core.network.core.connection.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.PassiveState
import java.net.InetSocketAddress

object PassiveStateInitializer {
  fun createActiveState(
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
      clusterNodesHandler = clusterNodesHandler,
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