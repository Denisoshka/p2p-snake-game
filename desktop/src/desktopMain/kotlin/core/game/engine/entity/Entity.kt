package d.zhdanov.ccfit.nsu.core.game.engine.entity

import d.zhdanov.ccfit.nsu.core.game.engine.map.EntityOnMapInfo
import d.zhdanov.ccfit.nsu.core.game.engine.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

interface Entity {
  val hitBox: MutableList<EntityOnMapInfo>
  var type: GameType
  var alive: Boolean
  fun checkCollisions(entity: Entity, context: GameEngine)
  fun update(context: GameEngine)
  fun shootState(context: GameEngine, state: StateMsg)
  fun atDead(context: GameEngine)
  fun restoreHitbox(offsets: List<Coord>)
}