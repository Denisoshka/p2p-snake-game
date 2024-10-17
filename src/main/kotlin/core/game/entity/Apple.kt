package d.zhdanov.ccfit.nsu.core.game.entity

import d.zhdanov.ccfit.nsu.core.game.Context
import d.zhdanov.ccfit.nsu.core.game.map.GameType
import d.zhdanov.ccfit.nsu.core.game.map.MapPoint

class Apple(x: Int, y: Int) : Entity {
  private var isDead: Boolean = false
  private val position: List<MapPoint>
  private val point: MapPoint = MapPoint(x, y, GameType.Apple)

  init {
    position = listOf(point)
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

  override fun getHitBox(): Iterable<MapPoint> {
    return position;
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