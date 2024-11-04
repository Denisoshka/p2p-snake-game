package d.zhdanov.ccfit.nsu.core.game.entity

import d.zhdanov.ccfit.nsu.core.game.states.impl.GameState
import d.zhdanov.ccfit.nsu.core.game.map.EntityOnMapInfo

class Apple(x: Int, y: Int, context: GameState) : Entity {
  private var isDead: Boolean = false
  private val hitBox: List<EntityOnMapInfo>
  private val point: EntityOnMapInfo = EntityOnMapInfo(x, y, GameType.Apple)

  init {
    hitBox = listOf(point)
    context.map.addEntity(this)
  }

  override fun checkCollisions(entity: Entity, context: GameState) {
    if (isDead) return
    if (entity.getHitBox()
        .any { box -> box.x == point.x && box.y == point.y }
    ) {
      isDead = true
    }
  }

  override fun update(context: GameState) {}

  override fun getHitBox(): Iterable<EntityOnMapInfo> {
    return hitBox;
  }

  override fun getType(): GameType {
    return GameType.Apple
  }

  override fun getScore(): Int {
    return 0
  }

  override fun isDead(): Boolean {
    return isDead
  }

  override fun getId(): Int {
    return 0;
  }

  override fun shootState(state: GameState) {
    state.foods.add(GameState.Coord(point.x, point.y))
  }
}