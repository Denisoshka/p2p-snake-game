package d.zhdanov.ccfit.nsu.core.game.engine.entity

import d.zhdanov.ccfit.nsu.core.game.engine.GameMap
import d.zhdanov.ccfit.nsu.core.game.engine.impl.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

interface Entity {
  val hitBox: MutableList<GameMap.Cell>
  val type: GameType
  var alive: Boolean
  fun update(context: GameEngine, sideEffects: List<Entity>)
  fun checkCollisions(entity: Entity, context: GameEngine)
  fun restoreState(offsets: List<Coord>)
  fun atDead(context: GameEngine)
  fun shootState(state: StateMsg)
}