package d.zhdanov.ccfit.nsu.core.network.interfaces.core

import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl.GameNode
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

interface StateConsumer {
  fun submitState(
    state: StateMsg, acceptedPlayers: List<Pair<Pair<GameNode, String>, ActiveEntity?>>
  )
}