package d.zhdanov.ccfit.nsu.core.game.engine.entity.active

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.GameContext
import d.zhdanov.ccfit.nsu.core.game.engine.GameMap
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType
import d.zhdanov.ccfit.nsu.core.game.engine.entity.passive.AppleEntity
import d.zhdanov.ccfit.nsu.core.game.engine.impl.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import kotlin.random.Random

private const val FoodSpawnChance = 0.5


open class SnakeEntity(
  override val id: Int,
  @Volatile var direction: Direction,
  private val gameContext: GameContext,
  x: Int? = null,
  y: Int? = null,
) : ActiveEntity {
  private var prevDir: Direction = direction
  override var alive: Boolean = true
  override val type: GameType = GameType.Snake
  private var eaten = false
  final override val hitBox: MutableList<GameMap.Cell> = ArrayList(2)
  @Volatile var snakeState = SnakeState.ALIVE
  var score: Int = 0
    private set
  override val head = hitBox.first()
  
  init {
    if(x != null && y != null) {
      hitBox.add(GameMap.Cell(x, y))
      hitBox.add(GameMap.Cell(x - direction.dx, y - direction.dy))
    }
  }
  
  constructor(
    snake: SnakesProto.GameState.Snake, score: Int, gameContext: GameContext,
  ) : this(
    snake.playerId, Direction.fromProto(snake.headDirection), gameContext
  ) {
    this.score = score
    this.snakeState = SnakeState.fromProto(snake.state)
    restoreHitBox(snake.pointsList)
  }
  
  private fun restoreHitBox(offsets: List<SnakesProto.GameState.Coord>) {
    if(hitBox.isNotEmpty()) throw RuntimeException("ну и пиздец")
    for(offset in offsets) {
      hitBox.add(GameMap.Cell(offset.x, offset.y))
    }
  }
  
  override fun shootState(state: SnakesProto.GameState.Builder) {
    val cordsShoot = hitBox.map {
      SnakesProto.GameState.Coord.newBuilder().setX(it.x).setY(it.y)
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
  
  override fun atDead(context: GameEngine) {
    hitBoxTravel { x, y ->
      if(Random.nextDouble() < FoodSpawnChance) {
        context.sideEffectEntity.add(AppleEntity(x, y))
      }
    }
  }
  
  private val afterHeadIndex = 1
  override fun update(context: GameEngine, sideEffects: List<Entity>) {
    gameContext.gameMap.apply {
      head.x = getFixedX(head.x + direction.dx)
      head.y = getFixedY(head.y + direction.dy)
    }
    
    gameContext.gameMap.apply {
      if(!directionChanged()) {
        hitBox[afterHeadIndex].apply {
          x += direction.dx
          y += direction.dy
        }
      } else {
        hitBox.add(
          afterHeadIndex, GameMap.Cell(direction.dx, direction.dy)
        )
      }
    }
    
    if(!eaten) {
      val tail = hitBox.last()
      if(tail.x != 0) {
        if(tail.x > 0) {
          --tail.x
        } else {
          ++tail.x
        }
        
        if(tail.x == 0) {
          hitBox.removeLast()
        }
      } else {
        if(tail.y > 0) {
          --tail.y
        } else {
          tail.y++
        }
        
        if(tail.y == 0) {
          hitBox.removeLast()
        }
      }
    }
    eaten = false
    prevDir = direction
  }
  
  override fun checkCollisions(
    entity: Entity, context: GameEngine
  ) {
    when(entity) {
      is SnakeEntity -> {
        hitBoxTravel { x, y ->
          if(head.x == x && head.y == y) {
            alive = false
            return@hitBoxTravel
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
  
  fun changeState(direction: Direction) {
    if(!isOpposite(direction)) {
      this.prevDir = this.direction
      this.direction = direction
    }
  }
  
  override fun hitBoxTravel(function: (x: Int, y: Int) -> Unit) {
    var x = head.x
    var y = head.y
//    function(x, y)
    hitBox.drop(1).forEach {
      if(it.y != 0) {
        var offY = it.y
        while(offY != 0) {
          if(offY > 0) {
            y = gameContext.gameMap.getFixedY(y + 1)
            --offY
          } else {
            y = gameContext.gameMap.getFixedY(y - 1)
            ++offY
          }
          function(x, y)
        }
      } else {
        var offX = it.x
        while(offX != 0) {
          if(offX > 0) {
            x = gameContext.gameMap.getFixedX(x + 1)
            --offX
          } else {
            x = gameContext.gameMap.getFixedX(x - 1)
            ++offX
          }
          function(x, y)
        }
      }
    }
  }
  
  private fun isOpposite(newDirection: Direction): Boolean {
    return direction.opposite() == newDirection
  }
  
  private fun directionChanged(): Boolean = direction != prevDir
}