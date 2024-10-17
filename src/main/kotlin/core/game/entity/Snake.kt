package d.zhdanov.ccfit.nsu.core.game.entity

import d.zhdanov.ccfit.nsu.core.game.Context
import d.zhdanov.ccfit.nsu.core.game.map.MapPoint
import d.zhdanov.ccfit.nsu.core.messages.Direction

class Snake(
  mapPoint: MapPoint,
  val id: Int,
  var direction: Direction,
  context: Context
) : Entity {
  private var isDead: Boolean = false;
  private var score: Int = 0
  private val body: MutableList<MapPoint> = mutableListOf(
    mapPoint.apply {
      gameType = GameType.Snake;
      ownerId = id
    },
    MapPoint(
      mapPoint.x - direction.dx,
      mapPoint.y - direction.dy,
      GameType.Snake
    ).apply { ownerId = id },
  )

  init {
    context.map.addEntity(this)
  }

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
    val tail = body.removeLast()
    context.map.movePoint(
      tail,
      body[0].x + direction.dx,
      body[0].y + direction.dy
    )
    body.add(0, tail)
  }

  override fun checkCollisions(entity: Entity, context: Context) {
    val head = getHead()
    if (entity.getHitBox()
        .any { point -> point.x == head.x && point.y == head.y }
    ) {
      when (entity.getType()) {
        GameType.Snake -> isDead = true
        GameType.Apple -> if (!entity.isDead()) ++score
        else -> {}
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
    return direction.opposite() == newDirection
  }
}