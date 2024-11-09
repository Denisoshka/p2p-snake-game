package d.zhdanov.ccfit.nsu.core.game.engine.entity.stardart

import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType
import d.zhdanov.ccfit.nsu.core.game.engine.map.EntityOnMapInfo
import d.zhdanov.ccfit.nsu.core.game.states.impl.GameState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Snake
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

class Snake(
  context: GameState,
  startX: Int,
  startY: Int,
  var direction: Direction,
  val id: Int,
) : Entity {
  override var alive: Boolean = true
  override var type: GameType = GameType.Snake
  override val hitBox: MutableList<EntityOnMapInfo> = ArrayList(2)
  var snakeState = SnakeState.ALIVE
  var score: Int = 0
    private set

  init {
    hitBox.add(EntityOnMapInfo(startX, startY))
    hitBox.add(EntityOnMapInfo(startX - direction.dx, startY - direction.dy))
    context.map.addEntity(this)
  }

  override fun shootState(context: GameState, state: StateMsg) {
    val head = hitBox.first()
    val cordsShoot = ArrayList<Coord>(hitBox.size)
    for(cord in hitBox) {
      cordsShoot.add(Coord(cord.x - head.x, cord.y - head.y))
    }
    cordsShoot.first().x = head.x
    cordsShoot.first().y = head.y

    val shoot = Snake(snakeState, id, cordsShoot, direction)
    state.snakes.add(shoot)
  }

  fun getScore(): Int {
    return score
  }

  override fun update(context: GameState) {
    hitBox.removeLast()
    val point = EntityOnMapInfo(
      hitBox[0].x + direction.dx, hitBox[0].y + direction.dy
    )
//  todo мб нужно сделать так чтобы здесь выделялась новая точка и не было
//   проблем с снятием снапшота
    context.map.movePoint(
      point, hitBox[0].x + direction.dx, hitBox[0].y + direction.dy
    )
    hitBox.add(0, point)
    TODO("fix this")
  }

  override fun checkCollisions(
    entity: Entity, context: GameState
  ) {
    val head = getHead()
    if(entity.hitBox.any { point -> point.x == head.x && point.y == head.y }) {
      when(entity.type) {
        GameType.Snake -> alive = false
        GameType.Apple -> if(entity.alive) ++score
        else           -> {}
      }
    }
  }

  fun changeState(newDirection: Direction) {
    if(!isOpposite(newDirection)) direction = newDirection
  }

  private fun getHead() = hitBox.first()

  private fun isOpposite(newDirection: Direction): Boolean {
    return direction.opposite() == newDirection
  }
}