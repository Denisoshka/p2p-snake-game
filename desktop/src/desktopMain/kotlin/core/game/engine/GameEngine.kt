package d.zhdanov.ccfit.nsu.core.game.engine

import d.zhdanov.ccfit.nsu.core.game.GameConfig
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.PlayerT
import d.zhdanov.ccfit.nsu.core.game.engine.map.GameMap
import d.zhdanov.ccfit.nsu.states.State
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Snake
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class GameEngine(
  initialStateOrder: Int,
  private val gameConfig: GameConfig,
  private val gameController: GameController
) : State {
  private val entities: MutableList<Entity> = mutableListOf()
  private val players: MutableMap<Int, PlayerT> = HashMap()
  private val contextId: AtomicLong = AtomicLong(0)
  private val executor = Executors.newSingleThreadExecutor()
  private val delayMillis = 1000L / gameConfig.updatesPerSecond
  var stateOrder = initialStateOrder + 1
    private set

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

    for(entity in entities) {
      entity.update(this)
    }
    for((_, snake) in players) {
      snake.snake.update(this)
    }
  }

  private fun checkCollision() {
    for(x in entities) {
      for(y in entities) {
        if(x != y) x.checkCollisions(y, this);
      }
      for((_, snake) in players) {
        snake.snake.checkCollisions(x, this)
        x.checkCollisions(snake.snake, this)
      }
    }

    entities.removeIf { !it.alive }
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
    val snakeSnapshot = ArrayList<Snake>(players.size)
    val foodSnapshot = ArrayList<Coord>(entities.size)
    val playersSnapshot = ArrayList<GamePlayer>(players.size)
    val nextOrder = ++stateOrder;
    val state = StateMsg(
      nextOrder, snakeSnapshot, foodSnapshot, playersSnapshot
    )
    for(entity in entities) {
      entity.shootState(this, state)
    }
    for((_, player) in players) {
      player.shootState(this, state)
    }
    gameController.submitGameState(state)
  }

  fun getNextId(): Long {
    return contextId.getAndIncrement()
  }

  private fun addNewSnakes() {
    var addedSnakes = MutableList()
    for(num in 0 until gameConfig.maxSnakesQuantityAddedPerUpdate) {
      map.findFreeSquare()?.also {
        TODO()
      }
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