package d.zhdanov.ccfit.nsu.core.game.engine.impl

import core.network.core.connection.game.impl.ClusterNode
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.GameContext
import d.zhdanov.ccfit.nsu.core.game.engine.GameMap
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.SnakeEntity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.passive.AppleEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.*
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.network.interfaces.StateConsumer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors
import kotlin.random.Random

private val Logger = KotlinLogging.logger(GameEngine::class.java.name)

class GameEngine(
  private val joinInStateQ: Int,
  private val stateConsumer: StateConsumer,
  private val gameConfig: GameConfig,
) : GameContext {
  val map: GameMap = ArrayGameMap(gameConfig.width, gameConfig.height)
  
  private val sideEffectEntity: MutableList<Entity> = ArrayList()
  private val entities: MutableList<Entity> = ArrayList()
  
  private val executor = Executors.newSingleThreadExecutor()
  private val directions = Direction.entries.toTypedArray()
  
  private val joinBacklog = Channel<Pair<ClusterNode, String>>(joinInStateQ)
  private val joinedPlayers =
    ArrayList<Pair<Pair<ClusterNode, String>, ActiveEntity?>>(joinInStateQ)
  
  private fun spawnNewSnake(
    id: Int, direction: Direction? = null
  ): SnakeEntity? {
    val coords = map.findFreeSquare() ?: return null
    val dir = direction ?: directions[Random.nextInt(directions.size)]
    return SnakeEntity(coords.first, coords.second, dir, id)
  }
  
  private fun offerPlayer(playerInfo: ClusterNode, name: String): Boolean {
    return joinBacklog.trySend(playerInfo to name).isSuccess
  }
  
  private fun gameLoop() {
    try {
      while(Thread.currentThread().isAlive) {
        val startTime = System.currentTimeMillis()
        
        preprocess()
        update()
        checkCollision()
        
        val state = shootState()
        val joinedPlayersInfo = joinedPlayers
        stateConsumer.submitState(state, joinedPlayersInfo);
        
        val endTime = System.currentTimeMillis()
        val timeTaken = endTime - startTime
        val sleepTime = gameConfig.stateDelayMs - timeTaken
        
        if(sleepTime > 0) {
          Thread.sleep(sleepTime)
        }
      }
    } catch(e: InterruptedException) {
      Logger.info { "game shutdowned " }
      Thread.currentThread().interrupt()
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
  
  private fun preprocess() {
    sideEffectEntityPreprocess()
    joinedPlayersPreprocess()
    checkUpdateApplesPreprocess()
  }
  
  override fun shutdown() {
    Logger.info { "${GameEngine::class.java.name} ${this::shutdown.name}" }
    executor.shutdownNow()
  }
  
  override fun initGame(
    config: GameConfig, playerInfo: GamePlayerInfo, state: SnakesProto.GameMessage.StateMsg?
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
  }
  
  override fun initGame(
    config: GameConfig,
    playerInfo: SnakesProto.GameMessage.StateMsg?,
    gamePlayerInfo: GamePlayerInfo
  ): List<ActiveEntity> {
    val sn = spawnNewSnake(playerInfo.playerId) ?: throw RuntimeException(
      "snake not found"
    )
    return listOf(sn);
  }
  
  @Synchronized
  override fun launch() {
    Logger.info { "${GameEngine::class.java.name} launched with config $gameConfig" }
    executor.submit(this::gameLoop)
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
  
  private fun checkUpdateApplesPreprocess() {
    val applesQ = entities.count { it.type == GameType.Apple }
    gameConfig.foodStatic - applesQ
  }
  
  private fun sideEffectEntityPreprocess() {
    entities.addAll(sideEffectEntity)
    sideEffectEntity.forEach { map.addEntity(it) }
    sideEffectEntity.clear()
  }
  
  private fun joinedPlayersPreprocess() {
    joinedPlayers.clear()
    repeat(joinInStateQ) {
      val plInfo = joinBacklog.tryReceive().getOrNull() ?: return@repeat
      val snake = spawnNewSnake(plInfo.first.nodeId)
      joinedPlayers.add(plInfo to snake)
    }
  }
}