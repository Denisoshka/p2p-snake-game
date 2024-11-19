package d.zhdanov.ccfit.nsu.core.game.engine.entity

import d.zhdanov.ccfit.nsu.core.game.engine.GameEngine
import d.zhdanov.ccfit.nsu.core.game.engine.map.EntityOnMapInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.SnapshotableEntity

interface Entity : SnapshotableEntity {
  val hitBox: MutableList<EntityOnMapInfo>
  var type: GameType
  var alive: Boolean
  fun checkCollisions(entity: Entity, context: GameEngine)
  fun update(context: GameEngine, sideEffects: List<Entity>)
  fun atDead(context: GameEngine)
}