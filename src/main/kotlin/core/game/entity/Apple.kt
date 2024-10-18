package d.zhdanov.ccfit.nsu.core.game.entity

import d.zhdanov.ccfit.nsu.core.game.Context
import d.zhdanov.ccfit.nsu.core.game.map.EntityOnMapInfo

class Apple(x: Int, y: Int, context: Context) : Entity {
  private var isDead: Boolean = false
  private val hitBox: List<EntityOnMapInfo>
  private val point: EntityOnMapInfo = EntityOnMapInfo(x, y, GameType.Apple)

  init {
    hitBox = listOf(point)
    context.map.addEntity(this)
  }

  override fun checkCollisions(entity: Entity, context: Context) {
    if (isDead) return
    if (entity.getHitBox()
        .any { box -> box.x == point.x && box.y == point.y }
    ) {
      isDead = true
    }
  }

  override fun update(context: Context) {}

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
}