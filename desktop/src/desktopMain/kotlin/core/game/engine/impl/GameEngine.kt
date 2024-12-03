package d.zhdanov.ccfit.nsu.core.game.engine.impl

import core.network.core.Node
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.GameContext
import d.zhdanov.ccfit.nsu.core.game.engine.GameMap
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.SnakeEntity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.passive.AppleEntity
import d.zhdanov.ccfit.nsu.core.game.exceptions.IllegalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Snake
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.network.interfaces.StateConsumer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors
import kotlin.random.Random

private val Logger = KotlinLogging.logger(GameEngine::class.java.name)
private const val GameConfigIsNull = "Game config null"

class GameEngine(
  private val joinInStateQ: Int,
  private val stateConsumer: StateConsumer,
) : GameContext {
  private val sideEffectEntity: MutableList<Entity> = ArrayList()
  private val entities: MutableList<Entity> = ArrayList()
  val map: GameMap = ArrayGameMap(0, 0)

  private val executor = Executors.newSingleThreadExecutor()
  private val directions = Direction.entries.toTypedArray()
  private var gameConfig: InternalGameConfig? = null

  private val joinBacklog = Channel<Pair<Node, String>>(joinInStateQ)
  private val joinedPlayers =
    ArrayList<Pair<Pair<Node, String>, ActiveEntity?>>(joinInStateQ)

  private fun spawnNewSnake(id: Int, direction: Direction? = null): SnakeEntity? {
    val coords = map.findFreeSquare() ?: return null
    val dir = direction ?: directions[Random.nextInt(directions.size)]
    return SnakeEntity(coords.first, coords.second, dir, id)
  }

  private fun offerPlayer(playerInfo: Node, name: String): Boolean {
    return joinBacklog.trySend(playerInfo to name).isSuccess
  }

  private fun gameLoop(gameConfig: InternalGameConfig) {
    val gameSettings = synchronized(this) {
      gameConfig.gameSettings
    } /*happens before action lol*/

    while(Thread.currentThread().isAlive) {
      val startTime = System.currentTimeMillis()

      preprocess(gameSettings)
      update()
      checkCollision()

      val state = shootState()
      val joinedPlayersInfo = joinedPlayers
      stateConsumer.submitState(state, joinedPlayersInfo);

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
        x.checkCollisions(y, this);
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
    val state = StateMsg(
      0, snakeSnapshot, foodSnapshot, mutableListOf()
    )
    for(entity in entities) {
      entity.shootState(state)
    }
    return state
  }

  private fun preprocess(
    gameConfig: GameConfig
  ) {
    sideEffectEntityPreprocess(gameConfig);
    joinedPlayersPreprocess(gameConfig)
    checkUpdateApplesPreprocess(gameConfig)
  }

  @Synchronized
  override fun shutdownNow() {
    Logger.info { "${GameEngine::class.java.name} launched" }
    executor.shutdownNow()
  }

  override fun initGameFromState(
    config: InternalGameConfig, state: StateMsg
  ): List<ActiveEntity> {
    val ret = ArrayList<ActiveEntity>()

    state.snakes.forEach {
      val sn = restoreSnake(it)
      ret.add(sn)
    }

    state.foods.forEach(
      this::restoreApples
    )

    return ret
    TODO("make konfig use")
  }

  override fun initNewGame(config: InternalGameConfig): List<ActiveEntity> {
    val sn = spawnNewSnake(0) ?: throw RuntimeException("snake not found")
    return listOf(sn);
  }

  @Synchronized
  override fun launch() {
    val conf = gameConfig ?: throw IllegalGameConfig(GameConfigIsNull)
    Logger.info { "${GameEngine::class.java.name} launched with config $conf" }
    executor.submit {
      gameLoop(conf)
    }
  }

  private fun restoreSnake(snakeInfo: Snake): SnakeEntity {
    val snake = SnakeEntity(snakeInfo.direction, snakeInfo.playerId)
//    TODO(а если змея уже готова то что делать нам, вообще она должна себя
//     на карте метить или как?)
    snake.restoreState(snakeInfo.cords)
    entities.add(snake)
    return snake
  }

  private fun restoreApples(foodInfo: Coord): AppleEntity {
    val apple = AppleEntity(foodInfo.x, foodInfo.y)
    entities.add(apple);
    return apple
  }

  override fun addSideEntity(entity: Entity) {
    sideEffectEntity.add(entity)
  }

  private fun checkUpdateApplesPreprocess(gameConfig: GameConfig) {
    val applesQ = entities.count { it.type == GameType.Apple }
    gameConfig.foodStatic - applesQ
  }

  private fun sideEffectEntityPreprocess(gameConfig: GameConfig) {
    entities.addAll(sideEffectEntity)
    sideEffectEntity.forEach { map.addEntity(it) }
    sideEffectEntity.clear()
  }

  private fun joinedPlayersPreprocess(gameConfig: GameConfig) {
    joinedPlayers.clear()
    repeat(joinInStateQ) {
      val plInfo = joinBacklog.tryReceive().getOrNull() ?: return@repeat
      val snake = spawnNewSnake(plInfo.first.id)
      joinedPlayers.add(plInfo to snake)
    }
  }
}