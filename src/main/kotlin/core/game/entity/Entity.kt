package d.zhdanov.ccfit.nsu.core.game.entity

import d.zhdanov.ccfit.nsu.core.game.Context
import d.zhdanov.ccfit.nsu.core.game.map.GameType
import d.zhdanov.ccfit.nsu.core.game.map.MapPoint

interface Entity {
  fun checkCollisions(entity: Entity, context: Context)
  fun update(context: Context)
  fun getHitBox(): Iterable<MapPoint>
  fun getType(): GameType
  fun getScore(): Int
  fun isDead(): Boolean
  fun getId(): Int
}