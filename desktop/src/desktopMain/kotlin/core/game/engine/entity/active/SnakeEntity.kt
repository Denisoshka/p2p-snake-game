package d.zhdanov.ccfit.nsu.core.game.engine.entity.active

import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType
import d.zhdanov.ccfit.nsu.core.game.engine.entity.passive.AppleEntity
import d.zhdanov.ccfit.nsu.core.game.engine.impl.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Snake
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import kotlin.random.Random

private const val FoodSpawnChance = 0.5

open class SnakeEntity(
  @Volatile var direction: Direction,
  override val id: Int,
) : ActiveEntity {
  
  constructor(x: Int, y: Int, direction: Direction, id: Int) : this(
    direction, id
  ) {
  }
  
  override var alive: Boolean = true
  override val type: GameType = GameType.Snake
  override val hitBox: MutableList<EntityOnMapInfo> = ArrayList(2)
  var score: Int = 0
  @Volatile var snakeState = SnakeState.ALIVE
  
  override fun restoreState(offsets: List<Coord>) {
    if(hitBox.isNotEmpty()) throw RuntimeException("братан змея уже готова")
    
    val currentPoint = offsets.first()
    for(nextOffset in offsets.drop(1)) {
      val nextX = currentPoint.x + nextOffset.x
      val nextY = currentPoint.y + nextOffset.y
      
      if(currentPoint.x != nextX && currentPoint.y == nextY) {
        val rangeX = if(nextX > currentPoint.x) {
          currentPoint.x..nextX
        } else {
          currentPoint.x downTo nextX
        }
        
        for(x in rangeX.drop(1)) {
          hitBox.add(EntityOnMapInfo(x, currentPoint.y))
        }
      } else if(currentPoint.y != nextY && currentPoint.x == nextX) {
        val rangeY = if(nextY > currentPoint.y) {
          currentPoint.y..nextY
        } else {
          currentPoint.y downTo nextY
        }
        
        for(y in rangeY.drop(1)) {
          hitBox.add(EntityOnMapInfo(currentPoint.x, y))
        }
      }
      currentPoint.x = nextX
      currentPoint.y = nextY
      
      hitBox.add(EntityOnMapInfo(nextX, nextY))
    }
  }
  
  override fun shootState(state: StateMsg) {
    val head = hitBox.first()
    val cordsShoot = ArrayList<Coord>()
    cordsShoot.add(Coord(head.x, head.y))
    var curPoint = hitBox.first()
    var offsetX = 0;
    var offsetY = 0;
    for(nextPoint in hitBox.drop(1)) {
      if(curPoint.x != nextPoint.x && curPoint.y == nextPoint.y) {
        offsetX += nextPoint.x - curPoint.x
      } else if(curPoint.y != nextPoint.y && curPoint.x == nextPoint.x) {
        offsetY += nextPoint.y - curPoint.y
      } else {
        if(offsetX != 0) cordsShoot.add(Coord(offsetX, 0))
        else if(offsetY != 0) cordsShoot.add(Coord(0, offsetY))
        
        offsetX = 0
        offsetY = 0
        
        if(curPoint.x != nextPoint.x && curPoint.y == nextPoint.y) {
          offsetX = nextPoint.x - curPoint.x
        } else if(curPoint.y != nextPoint.y && curPoint.x == nextPoint.x) {
          offsetY = nextPoint.y - curPoint.y
        }
      }
      curPoint = nextPoint
    }
    if(offsetX != 0) cordsShoot.add(Coord(offsetX, 0))
    else if(offsetY != 0) cordsShoot.add(Coord(0, offsetY))
    
    val shoot = Snake(snakeState, id, cordsShoot, direction)
    state.snakes.add(shoot)
  }
  
  override fun atDead(context: GameEngine) {
    for(cord in hitBox) {
      if(Random.nextDouble() < FoodSpawnChance) {
        context.addSideEntity(AppleEntity(cord.x, cord.y))
      }
    }
  }
  
  fun getScore(): Int {
    return score
  }
  
  override fun update(context: GameEngine, sideEffects: List<Entity>) {
    hitBox.removeLast()

//  todo мб нужно сделать так чтобы здесь выделялась новая точка и не было
//   проблем с снятием снапшота
    
    hitBox.add(0, point)
    TODO("fix this")
  }
  
  override fun checkCollisions(
    entity: Entity, context: GameEngine
  ) {
    if(!alive) return
    val head = hitBox.first()
    
    if(entity.hitBox.any { point -> point.x == head.x && point.y == head.y }) {
      when(entity) {
        is SnakeEntity -> {
          if(this === entity) {
            TODO()
          } else {
            entity.score++
            alive = false
          }
        }
        
        is AppleEntity -> if(entity.alive) ++score
        else           -> {}
      }
    }
  }
  
  fun changeState(direction: Direction) {
    if(!isOpposite(direction)) this.direction = direction
  }
  
  private fun isOpposite(newDirection: Direction): Boolean {
    return direction.opposite() == newDirection
  }
}