package d.zhdanov.ccfit.nsu.core.game.entity

import d.zhdanov.ccfit.nsu.core.game.states.impl.GameState
import d.zhdanov.ccfit.nsu.core.game.map.EntityOnMapInfo

interface Entity {
  fun checkCollisions(entity: Entity, context: GameState)
  fun update(context: GameState)
  fun getHitBox(): Iterable<EntityOnMapInfo>
  fun getType(): GameType
  fun isDead(): Boolean
  fun shootState(state: GameState)
}