package d.zhdanov.ccfit.nsu.core.game.engine

import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.GameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Player
import d.zhdanov.ccfit.nsu.core.game.engine.entity.standart.Snake
import d.zhdanov.ccfit.nsu.core.game.engine.map.GameMap
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.states.State
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class GameEngine(
  private val gameConfig: GameConfig,
  private val gameController: GameController,
  private val state: StateMsg? = null,
) : State {
  private val entities: MutableList<Entity> = mutableListOf()
  private val sideEffectEntity: MutableList<Entity> = mutableListOf()
  private val players: MutableMap<Int, Player> = HashMap()
  private val contextId: AtomicLong = AtomicLong(0)
  private val executor = Executors.newSingleThreadExecutor()
  private val delayMillis = 1000L / gameConfig.updatesPerSecond
  private var stateOrder = state?.stateOrder ?: 0

  init {
    state?.let { restoreContext(state, this) }
  }

  fun addSideEffect(entity: Entity) {
    sideEffectEntity.add(entity)
  }

  fun addEntity(entity: Entity) {
    map.addEntity(entity)
    entities.add(entity)
  }

  fun addPlayer(player: Player) {
    players[player.snake.id] = player
    map.addEntity(player)
  }

  fun restoreContext(state: StateMsg, gameEngine: GameEngine) {
    for(entity in gameEngine.entities) {
      when(entity.type) {
        GameType.Snake -> {

        }

        GameType.Apple -> {

        }

        GameType.None  -> {

        }
      }
    }
    for(pl in state.players) {
      sn = Snake()
    }
  }

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
    updatePreprocess()

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


    val entrIt = entities.iterator()
    while(entrIt.hasNext()) {
      val ent = entrIt.next()
      if(!ent.alive) {
        entrIt.remove()
        ent.atDead(this)
      }
    }
    val plIt = players.iterator()
    while(plIt.hasNext()) {
      val (_, player) = plIt.next()
      if(!player.alive) {
        plIt.remove()
        player.atDead(this)
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

  private fun updatePreprocess() {
    entities.addAll(sideEffectEntity)
    for(se in sideEffectEntity) map.addEntity(se)
    sideEffectEntity.clear()
  }

  override fun terminate() {
    executor.shutdownNow()
  }

  override fun launch() {
    executor.submit {
      gameLoop()
    }
  }

  fun restoreState() {

  }

  override fun launch(state: State) {

  }

}