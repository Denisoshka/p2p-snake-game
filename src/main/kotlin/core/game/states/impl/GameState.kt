package d.zhdanov.ccfit.nsu.core.game.states.impl

import d.zhdanov.ccfit.nsu.core.game.GameConfig
import d.zhdanov.ccfit.nsu.core.game.GameController
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.PlayerT
import d.zhdanov.ccfit.nsu.core.game.engine.entity.stardart.Snake
import d.zhdanov.ccfit.nsu.core.game.engine.map.GameMap
import d.zhdanov.ccfit.nsu.core.game.states.State
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class GameState(
  private val gameConfig: GameConfig,
  private val gameController: GameController,

  ) : State {
  private val gameObjects: MutableList<Entity> = mutableListOf()
  private val players: MutableMap<Long, PlayerT> = HashMap()
  private val contextId: AtomicLong = AtomicLong(0)
  private val executor = Executors.newSingleThreadExecutor()
  private val delayMillis = 1000L / gameConfig.updatesPerSecond

  val map: GameMap = TODO()

  private fun gameLoop() {
    while(Thread.currentThread().isAlive) {
      val startTime = System.currentTimeMillis()

      update()
      checkCollision()
      shootState()

      val endTime = System.currentTimeMillis()
      val timeTaken = endTime - startTime
      val sleepTime = delayMillis - timeTaken

      if(sleepTime > 0) Thread.sleep(sleepTime)
    }

    TODO("нужно сделать возможность прервать игру?")
  }

  private fun update() {
    addNewSnakes()

    for(entity in gameObjects) {
      entity.update(this)
    }
    for((_, snake) in players) {
      snake.snake.update(this)
    }
  }

  private fun checkCollision() {
    for(x in gameObjects) {
      for(y in gameObjects) {
        if(x != y) x.checkCollisions(y, this);
      }
      for((_, snake) in players) {
        if(x != snake.snake) {
          snake.snake.checkCollisions(x, this)
          x.checkCollisions(snake.snake, this)
        }
      }
    }

    gameObjects.removeIf { !it.alive }
    val it = players.iterator()
    while(it.hasNext()) {
      val (_, player) = it.next()
      if(!player.alive) {
        it.remove()
        player.onObservedExpired()
      }
    }
  }

  private fun shootState() {
    val snakeSnapshot = mutableListOf<Snake>()
    val foodSnapshot = mutableListOf<Snake>()
    for(entity in gameObjects) {
      entity.shootState()
    }
    val stateMsg = StateMsg()
    gameController.submitGameState(stateMsg)
  }

  fun getNextId(): Long {
    return contextId.getAndIncrement()
  }

  private fun onSnakeDead(snakeId: Long) {
    players.remove(snakeId)
  }

  private fun addNewSnakes() {
    var addedSnakes = MutableList()
    for(num in 0 until gameConfig.maxSnakesQuantityAddedPerUpdate) {
      map.findFreeSquare()?.also {
      }
    }
  }

  fun submitNewSnake() {

  }

  override fun terminate() {
    executor.shutdownNow()
  }

  override fun launch() {
    executor.submit {
      gameLoop()
    }
  }
}