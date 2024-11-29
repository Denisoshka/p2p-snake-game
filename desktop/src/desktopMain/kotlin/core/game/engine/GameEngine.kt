package d.zhdanov.ccfit.nsu.core.game.engine

import core.network.core.Node
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType
import d.zhdanov.ccfit.nsu.core.game.engine.entity.standart.SnakeEnt
import d.zhdanov.ccfit.nsu.core.game.engine.map.EntityOnMapInfo
import d.zhdanov.ccfit.nsu.core.game.engine.map.GameMap
import d.zhdanov.ccfit.nsu.core.game.exceptions.IllegalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Snake
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors
import kotlin.random.Random

private val Logger = KotlinLogging.logger(GameEngine::class.java.name)
private const val GameConfigIsNull = "Game config null"

class GameEngine(
  private val joinInStateQ: Int, private var stateOrder: Int = 0
) {
  val sideEffectEntity: MutableList<Entity> = mutableListOf()
  private val entities: MutableList<Entity> = mutableListOf()
  val map: GameMap = TODO()

  private val executor = Executors.newSingleThreadExecutor()
  private val directions = Direction.entries.toTypedArray()
  private var gameConfig: InternalGameConfig? = null

  private val joinBacklog = Channel<Pair<Node, String>>(joinInStateQ)
  private val joinedPlayers =
    ArrayList<Pair<Pair<Node, String>, SnakeEnt?>>(joinInStateQ)

  fun spawnSnake(id: Int, direction: Direction? = null): SnakeEnt? {
    val coords = map.findFreeSquare() ?: return null
    val dir = direction ?: directions[Random.nextInt(directions.size)]
    return SnakeEnt(dir, id).apply {
      hitBox.add(EntityOnMapInfo(coords.x, coords.y))
      hitBox.add(EntityOnMapInfo(coords.x + dir.dx, coords.y + dir.dy))
    }
  }

  private fun offerPlayer(playerInfo: Node, name: String): Boolean {
    return joinBacklog.trySend(playerInfo to name).isSuccess
  }

  private fun gameLoop(gameConfig: InternalGameConfig) {
    synchronized(this) {} /*happens before action lol*/
    val gameSettings = gameConfig.gameSettings
    while(Thread.currentThread().isAlive) {
      val startTime = System.currentTimeMillis()
      updatePreprocess(sideEffectEntity, gameSettings)

      update()
      checkCollision()
      val state = shootState()

      val endTime = System.currentTimeMillis()
      val timeTaken = endTime - startTime
      val sleepTime = gameSettings.stateDelayMs - timeTaken

      if(sleepTime > 0) {
        Thread.sleep(sleepTime)
      }
    }
  }

  private fun update() {
    for(entity in entities) {
      entity.update(this, sideEffectEntity)
    }
  }

  private fun checkCollision() {
    for(x in entities) {
      for(y in entities) {
        if(x != y) x.checkCollisions(y, this);
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
  }

  private fun shootState(): StateMsg {
    val snakeSnapshot = ArrayList<Snake>()
    val foodSnapshot = ArrayList<Coord>()
    val nextOrder = ++stateOrder;
    val state = StateMsg(
      nextOrder, snakeSnapshot, foodSnapshot, mutableListOf()
    )
    for(entity in entities) {
      entity.shootState(state)
    }
    return state
  }

  private fun updatePreprocess(
    sideEffectEntity: MutableList<Entity>, gameConfig: GameConfig
  ) {
    entities.addAll(sideEffectEntity)

    val applesQ = entities.count { it.type == GameType.Apple }
    gameConfig.foodStatic - applesQ

    sideEffectEntity.forEach { map.addEntity(it) }
    sideEffectEntity.clear()
    joinedPlayers.clear()
    repeat(joinInStateQ) {
      val plInfo = joinBacklog.tryReceive().getOrNull() ?: return@repeat
      val snake = spawnSnake(plInfo.first.id)
      joinedPlayers.add(plInfo to snake)
    }
  }

  fun shutdownNow() {
    synchronized(this) {

      Logger.info { "${GameEngine::class.java.name} launched" }
      executor.shutdownNow()
    }
  }

  fun initContext(
    config: InternalGameConfig, entities: List<Entity>?
  ) {
    TODO("make map init")
    entities?.let { this.entities.addAll(it) }
    this.entities.forEach { map.addEntity(it) }
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