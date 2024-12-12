package d.zhdanov.ccfit.nsu.core.network.core.states

import core.network.core.connection.game.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder

interface GameStateT {
  suspend fun handleNodeDetach(
    node: ClusterNode,
    token: NetworkStateHolder.ChangeToken
  )
}