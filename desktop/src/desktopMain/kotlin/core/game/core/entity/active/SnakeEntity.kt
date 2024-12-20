package d.zhdanov.ccfit.nsu.core.game.core.entity.active

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.core.engine.GameContext
import d.zhdanov.ccfit.nsu.core.game.core.engine.GameMap
import d.zhdanov.ccfit.nsu.core.game.core.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.core.entity.GameType
import d.zhdanov.ccfit.nsu.core.game.core.entity.passive.AppleEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import kotlin.random.Random

private const val FoodSpawnChance = 0.5

private val directions = Direction.entries.toTypedArray()

open class SnakeEntity(
  override val id: Int,
  override val gameContext: GameContext,
  x: Int,
  y: Int,
) : ActiveEntity {
  @Volatile var direction: Direction = directions[Random.nextInt(
    0, directions.size
  )]
  private var prevDir: Direction = direction
  override var alive: Boolean = true
  override val type: GameType = GameType.Snake
  private var eaten = false
  private val _hitBox: MutableList<GameMap.Cell> = ArrayList(2)
  @Volatile var snakeState = SnakeState.ALIVE
  var score: Int = 0
    private set
  override val head = _hitBox.first()
  
  init {
    _hitBox.add(GameMap.Cell(x, y))
    _hitBox.add(GameMap.Cell(x - direction.dx, y - direction.dy))
  }
  
  constructor(
    snake: SnakesProto.GameState.Snake, score: Int, gameContext: GameContext,
  ) : this(
    snake.playerId, gameContext, 0, 0
  ) {
    this.direction = Direction.fromProto(snake.headDirection)
    this.prevDir = direction
    this.score = score
    this.snakeState = SnakeState.fromProto(snake.state)
    restoreHitBox(snake.pointsList)
  }
  
  private fun restoreHitBox(offsets: List<SnakesProto.GameState.Coord>) {
    _hitBox.clear()
    for(offset in offsets) {
      _hitBox.add(GameMap.Cell(offset.x, offset.y))
    }
  }
  
  override fun shootState(state: SnakesProto.GameState.Builder) {
    val cordsShoot = _hitBox.map {
      MessageUtils.MessageProducer.getCoordBuilder(it.x, it.y)
    }
    val snakeBuilder = MessageUtils.MessageProducer.getSnakeMsgBuilder(
      playerId = id,
      headWithOffsets = cordsShoot,
      snakeState = snakeState,
      direction = direction
    )
    state.apply {
      snakesBuilderList.add(snakeBuilder)
    }
  }
  
  override val hitBox: Iterable<Pair<Int, Int>> = Iterable {
    iterator {
      var currentX = head.x
      var currentY = head.y
      
      yield(Pair(currentX, currentY))
      
      for(offset in _hitBox) {
        repeat(kotlin.math.abs(offset.y)) {
          currentY = gameContext.gameMap.getFixedY(
            if(offset.y > 0) currentY + 1 else currentY - 1
          )
          yield(Pair(currentX, currentY))
        }
        
        repeat(kotlin.math.abs(offset.x)) {
          currentX = gameContext.gameMap.getFixedX(
            if(offset.x > 0) currentX + 1 else currentX - 1
          )
          yield(Pair(currentX, currentY))
        }
      }
    }
  }
  
  override fun atDead() {
    this.hitBox.forEach { (x, y) ->
      if(Random.nextDouble() < FoodSpawnChance) {
        gameContext.sideEffectEntity.add(AppleEntity(x, y, gameContext))
      }
    }
  }
  
  private val afterHeadIndex = 1
  override fun update() {
    gameContext.gameMap.apply {
      head.x = getFixedX(head.x + direction.dx)
      head.y = getFixedY(head.y + direction.dy)
    }
    
    gameContext.gameMap.apply {
      if(!directionChanged()) {
        _hitBox[afterHeadIndex].apply {
          x += direction.dx
          y += direction.dy
        }
      } else {
        _hitBox.add(
          afterHeadIndex, GameMap.Cell(direction.dx, direction.dy)
        )
      }
    }
    
    if(!eaten) {
      val tail = _hitBox.last()
      if(tail.x != 0) {
        if(tail.x > 0) {
          --tail.x
        } else {
          ++tail.x
        }
        
        if(tail.x == 0) {
          _hitBox.removeLast()
        }
      } else {
        if(tail.y > 0) {
          --tail.y
        } else {
          tail.y++
        }
        
        if(tail.y == 0) {
          _hitBox.removeLast()
        }
      }
    }
    eaten = false
    prevDir = direction
  }
  
  override fun checkCollisions(entity: Entity) {
    when(entity) {
      is SnakeEntity -> {
        this.hitBox.drop(1).forEach { (x, y) ->
          if(head.x == x && head.y == y) {
            alive = false
            return
          }
        }
        if(entity !== this && head.x == entity.head.x && head.y == entity.head.y) {
          alive = false
        }
      }
      
      is AppleEntity -> {
        if(entity.alive) {
          eaten = true
          ++score
        }
      }
    }
  }
  
  override fun changeState(direction: Direction) {
    if(!isOpposite(direction)) {
      this.prevDir = this.direction
      this.direction = direction
    }
  }
  
  private fun isOpposite(newDirection: Direction): Boolean {
    return direction.opposite() == newDirection
  }
  
  private fun directionChanged(): Boolean = direction != prevDir
}