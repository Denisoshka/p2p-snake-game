package d.zhdanov.ccfit.nsu.core.network.core.states

import core.network.core.connection.game.impl.ClusterNode

interface GameStateT {
  suspend fun handleNodeDetach(node: ClusterNode, changeAccessToken: Any)
}