package d.zhdanov.ccfit.nsu.core.game.states.impl

import d.zhdanov.ccfit.nsu.core.game.GameConfig
import d.zhdanov.ccfit.nsu.core.game.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.entity.Snake
import d.zhdanov.ccfit.nsu.core.game.map.GameMap
import d.zhdanov.ccfit.nsu.core.game.states.State
import d.zhdanov.ccfit.nsu.core.interaction.v1.bridges.PlayerContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class GameState(
  private val gameConfig: GameConfig
) : State {
  private val gameObjects: MutableList<Entity> = mutableListOf()
  private val snakes: MutableMap<Long, PlayerContext> = HashMap()
  private val contextId: AtomicLong = AtomicLong(0)
  private val executor = Executors.newSingleThreadExecutor()
  private val delayMillis = 1000L / gameConfig.updatesPerSecond

  val map: GameMap = TODO()

  private fun gameLoop() {
    while (Thread.currentThread().isAlive) {
      val startTime = System.currentTimeMillis()

      update()
      checkCollision()
      shootState()

      val endTime = System.currentTimeMillis()
      val timeTaken = endTime - startTime
      val sleepTime = delayMillis - timeTaken

      if (sleepTime > 0) Thread.sleep(sleepTime)
    }

    TODO("нужно сделать возможность прервать игру?")
  }

  private fun update() {
    addNewSnakes()

    for (entity in gameObjects) {
      entity.update(this)
    }
  }

  private fun checkCollision() {
    for (x in gameObjects) {
      for (y in gameObjects) {
        x.checkCollisions(y, this);
      }
    }
    gameObjects.removeIf { ent -> ent.isDead() }
  }

  private fun shootState(): StateMsg {
    val snakeSnapshot = mutableListOf<Snake>()
    val foodSnapshot = mutableListOf<Snake>()
    for (entity in gameObjects) {
      entity.shootState()
    }

  }

  fun updateSnake(snakeId: Long, steerMsg: SteerMsg, seq: Long) {
    val context = snakes[snakeId] ?: return
    context.update(steerMsg, seq)
  }

  fun getNextId(): Long {
    return contextId.getAndIncrement()
  }

  private fun onSnakeDead(snakeId: Long) {
    snakes.remove(snakeId)
  }

  private fun addNewSnakes() {
    for (num in 0 until gameConfig.maxSnakesQuantityAddedPerUpdate) {

    }
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