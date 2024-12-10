package d.zhdanov.ccfit.nsu.core.network.core.states

import core.network.core.connection.game.ClusterNodeT

interface GameStateT {
  suspend fun handleNodeDetach(
    node: ClusterNodeT
  )
}