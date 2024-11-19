package d.zhdanov.ccfit.nsu.core.game.engine

import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Player
import d.zhdanov.ccfit.nsu.core.game.engine.entity.standart.SnakeEnt
import d.zhdanov.ccfit.nsu.core.game.engine.map.EntityOnMapInfo
import d.zhdanov.ccfit.nsu.core.game.engine.map.GameMap
import d.zhdanov.ccfit.nsu.core.game.exceptions.IllegalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors
import kotlin.random.Random

private val Logger = KotlinLogging.logger(GameEngine::class.java.name)
private const val GameConfigIsNull = "Game config null"

class GameEngine(
  private var stateOrder: Int = 0
) {
  private val sideEffectEntity: MutableList<Entity> = mutableListOf()
  private val entities: MutableList<Entity> = mutableListOf()
  private val players: MutableMap<Int, Player> = HashMap()
  private val map: GameMap = TODO()

  private val executor = Executors.newSingleThreadExecutor()
  private val directions = Direction.entries.toTypedArray()
  private var gameConfig: InternalGameConfig? = null

  private val joinChannel: Channel<>()

  fun spawnSnake(id: Int, direction: Direction? = null): SnakeEnt? {
    val coords = map.findFreeSquare() ?: return null
    val dir = direction ?: directions[Random.nextInt(directions.size)]
    return SnakeEnt(dir, id).apply {
      hitBox.add(EntityOnMapInfo(coords.x, coords.y))
      hitBox.add(EntityOnMapInfo(coords.x + dir.dx, coords.y + dir.dy))
    }
  }

  private fun offerPlayer(): Boolean {

  }

  private fun gameLoop(gameConfig: GameConfig) {
    synchronized(this) {} /*happens before action lol*/
    while(Thread.currentThread().isAlive) {
      val startTime = System.currentTimeMillis()
      updatePreprocess(sideEffectEntity)

      update()
      checkCollision()
      val state = shootState()

      val endTime = System.currentTimeMillis()
      val timeTaken = endTime - startTime
      val sleepTime = gameConfig.stateDelayMs - timeTaken

      if(sleepTime > 0) {
        Thread.sleep(sleepTime)
      }
    }
  }

  private fun update() {
    for(entity in entities) {
      entity.update(this, sideEffectEntity)
    }
    for((_, snake) in players) {
      snake.snakeEnt.update(this, sideEffectEntity)
    }
  }

  private fun checkCollision() {
    for(x in entities) {
      for(y in entities) {
        if(x != y) x.checkCollisions(y, this);
      }
      for((_, snake) in players) {
        snake.snakeEnt.checkCollisions(x, this)
        x.checkCollisions(snake.snakeEnt, this)
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

  private fun shootState(): StateMsg {
    val snakeSnapshot =
      ArrayList<d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Snake>(players.size)
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
    return state
  }

  private fun updatePreprocess(sideEffectEntity: MutableList<Entity>) {
    entities.addAll(sideEffectEntity)
    for(se in sideEffectEntity) map.addEntity(se)
    sideEffectEntity.clear()
    var aplesToSpawnQ = 0;
    for()
  }

  fun shutdownNow() {
    synchronized(this) {
      Logger.info { "${GameEngine::class.java.name} launched" }
      executor.shutdownNow()
    }
  }

  fun initContext(
    config: InternalGameConfig, entities: List<Entity>?, players: List<Player>?
  ) {
    entities?.let {
      this.entities.addAll(it)
    }
    players?.forEach { this.players[it.snakeEnt.id] = it }
    TODO("пересобрать мапку из конфига")
  }

  fun launch() {
    synchronized(this) {
      val conf = gameConfig ?: throw IllegalGameConfig(GameConfigIsNull)
      Logger.info { "${GameEngine::class.java.name} launched with config $conf" }
      executor.submit {
        gameLoop(conf)
      }
    }
  }
}