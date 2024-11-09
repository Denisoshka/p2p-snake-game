package d.zhdanov.ccfit.nsu.core.game.engine.entity

import d.zhdanov.ccfit.nsu.core.game.engine.map.EntityOnMapInfo
import d.zhdanov.ccfit.nsu.core.game.states.impl.GameState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

interface Entity {
  val hitBox: MutableList<EntityOnMapInfo>
  var type: GameType
  var alive: Boolean
  fun checkCollisions(entity: Entity, context: GameState)
  fun update(context: GameState)
  fun shootState(context: GameState, state: StateMsg)
}