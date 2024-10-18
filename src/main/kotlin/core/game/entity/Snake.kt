package d.zhdanov.ccfit.nsu.core.game.entity

import d.zhdanov.ccfit.nsu.core.game.Context
import d.zhdanov.ccfit.nsu.core.game.map.EntityOnMapInfo
import d.zhdanov.ccfit.nsu.core.interaction.messages.Direction

class Snake(
  entityOnMapInfo: EntityOnMapInfo,
  val id: Int,
  var direction: Direction,
  context: Context
) : Entity {
  private var isDead: Boolean = false;
  private var score: Int = 0
  private val body: MutableList<EntityOnMapInfo> = mutableListOf(
    entityOnMapInfo.apply {
      gameType = GameType.Snake;
      ownerId = id
    },
    EntityOnMapInfo(
      entityOnMapInfo.x - direction.dx,
      entityOnMapInfo.y - direction.dy,
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

  override fun getHitBox(): Iterable<EntityOnMapInfo> {
    return body
  }

  override fun getScore(): Int {
    return score
  }

  override fun update(context: Context) {
    val tail = body.removeLast()
//  todo мб нужно сделать так чтобы здесь выделялась новая точка и не было
//   проблем с снятием снапшота
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

  private fun getHead(): EntityOnMapInfo = body.first()

  private fun isOpposite(newDirection: Direction): Boolean {
    return direction.opposite() == newDirection
  }
}