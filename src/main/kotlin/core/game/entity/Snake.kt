package d.zhdanov.ccfit.nsu.core.game.entity

import d.zhdanov.ccfit.nsu.core.game.Context
import d.zhdanov.ccfit.nsu.core.game.map.GameType
import d.zhdanov.ccfit.nsu.core.game.map.MapPoint
import d.zhdanov.ccfit.nsu.core.messages.Direction

class Snake(x: Int, y: Int, val id: Int, var direction: Direction) : Entity {
  private var isDead: Boolean = false;
  private var score: Int = 0
  private val body: MutableList<MapPoint> = mutableListOf(
    MapPoint(
      x, y,
      GameType.Snake
    ).apply { ownerId = id },
    MapPoint(
      x - direction.dx, y - direction.dy,
      GameType.Snake
    ).apply { ownerId = id },
  )

  override fun getType(): GameType {
    return GameType.Snake
  }

  override fun isDead(): Boolean {
    return isDead
  }

  override fun getId(): Int {
    return id
  }

  override fun getHitBox(): Iterable<MapPoint> {
    return body
  }

  override fun getScore(): Int {
    return score
  }

  override fun update(context: Context) {
    val head = MapPoint(
      body[0].x + direction.dx,
      body[0].y + direction.dy,
      GameType.Snake
    )
    head.ownerId = id
    body.add(0, head)
    body.removeAt(body.size - 1)
  }

  override fun checkCollisions(entity: Entity, context: Context) {
    val head = getHead()
    if (entity.getHitBox()
        .any { point -> point.x == head.x && point.y == head.y }
    ) {
      when (entity.getType()) {
        GameType.Snake -> isDead = true
        GameType.Apple -> if (!entity.isDead()) score++
      }
    }
  }

  fun changeState(newDirection: Direction) {
    if (!isOpposite(newDirection)) {
      direction = newDirection
    }
  }

  private fun getHead(): MapPoint = body.first()

  private fun isOpposite(newDirection: Direction): Boolean {
    return (direction == Direction.UP && newDirection == Direction.DOWN) ||
        (direction == Direction.DOWN && newDirection == Direction.UP) ||
        (direction == Direction.LEFT && newDirection == Direction.RIGHT) ||
        (direction == Direction.RIGHT && newDirection == Direction.LEFT)
  }
}