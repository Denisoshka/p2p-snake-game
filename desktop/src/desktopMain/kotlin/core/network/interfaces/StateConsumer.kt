package d.zhdanov.ccfit.nsu.core.network.interfaces

import core.network.core.Node
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

interface StateConsumer {
  fun submitState(
    state: StateMsg, acceptedPlayers: List<Pair<Pair<Node, String>, ActiveEntity?>>
  )
}