package d.zhdanov.ccfit.nsu.core.game.engine.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.GameMap
import d.zhdanov.ccfit.nsu.core.game.engine.NetworkGameContext
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.GameType
import d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe.ObservableSnakeEntity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.passive.AppleEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.IdService
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.interfaces.StateConsumer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.Executors

private val Logger = KotlinLogging.logger(NetworkGameEngine::class.java.name)

class NetworkGameEngine(
  private val joinInStateQ: Int,
  private val stateConsumer: StateConsumer,
  private val gameConfig: GameConfig,
  private val idService: IdService,
  override val registeredPlayers: MutableMap<InetSocketAddress, ObservableSnakeEntity>,
) : NetworkGameContext {
  override val gameMap: GameMap = ArrayGameMap(
    gameConfig.width, gameConfig.height
  )
  
  override val sideEffectEntity: MutableList<Entity> = ArrayList()
  override val entities: MutableList<Entity> = ArrayList()
  
  private val executorScope = CoroutineScope(
    Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  )
  private val joinBacklog =
    Channel<Pair<InetSocketAddress, SnakesProto.GameMessage>>(
      joinInStateQ
    )
  
  
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun CoroutineScope.gameDispatcher() = launch {
    try {
      var nextDelay = 0L
      while(isActive) {
        select {
          joinBacklog.onReceive {
            handleNewPlayer(it)
          }
          
          onTimeout(nextDelay) {
            nextDelay = countNextStep()
          }
        }
      }
    } catch(e: CancellationException) {
      Logger.info { "game shutdowned " }
      cancel()
    } finally {
      Logger.info { "game finished " }
    }
  }
  
  private fun spawnNewSnake(id: Int): ObservableSnakeEntity? {
    val coords = gameMap.findFreeSquare(GameType.Snake) ?: return null
    return ObservableSnakeEntity(
      id, this, coords.first, coords.second
    ).apply {
      sideEffectEntity.add(this)
    }
  }
  
  private fun handleNewPlayer(playerInfo: Pair<ClusterNodeT<Node.MsgInfo>, SnakesProto.GameMessage>) {
    val sn = spawnNewSnake(playerInfo.first.nodeId)?.apply {
      sideEffectEntity.add(this@apply)
    }
    stateConsumer.submitAcceptedPlayer(playerInfo to sn)
  }
  
  override fun offerPlayer(playerInfo: Pair<InetSocketAddress, SnakesProto.GameMessage>): Boolean {
    return joinBacklog.trySend(playerInfo).isSuccess
  }
  
  private fun countNextStep(): Long {
    val startTime = System.currentTimeMillis()
    
    update()
    checkCollision()
    postProcess()
    newStateAvailable()
    
    val endTime = System.currentTimeMillis()
    val timeTaken = endTime - startTime
    val sleepTime = gameConfig.stateDelayMs - timeTaken
    
    if(sleepTime > 0) {
      return sleepTime
    }
    return 0
  }
  
  private fun update() {
    for(entity in entities) {
      entity.update()
    }
  }
  
  private fun checkCollision() {
    for(x in entities) {
      for(y in entities) {
        x.checkCollisions(y);
      }
    }
    
    val entrIt = entities.iterator()
    while(entrIt.hasNext()) {
      val ent = entrIt.next()
      if(!ent.alive) {
        entrIt.remove()
        ent.atDead()
      }
    }
  }
  
  private fun newStateAvailable() {
    val stateBldr = SnakesProto.GameState.newBuilder()
    
    for(entity in entities) {
      entity.shootState(stateBldr)
    }
    
    stateConsumer.submitState(stateBldr)
  }
  
  private fun postProcess() {
    sideEffectEntityPostProcess()
    checkUpdateApplesPostProcess()
  }
  
  @Synchronized
  override fun shutdown() {
    Logger.info { "${NetworkGameEngine::class.java.name} ${this::shutdown.name}" }
    executorScope.cancel()
    joinBacklog.close()
  }
  
  override fun initGame(
    config: GameConfig,
    playerInfo: GamePlayerInfo,
    state: SnakesProto.GameMessage.StateMsg
  ): List<ObservableSnakeEntity> {
    val ret = ArrayList<ObservableSnakeEntity>()
    val pl = state.state.players.playersList.associateBy { it.id }
    state.state.snakesList.forEach {
      val plInfo = pl[it.playerId]
      if(plInfo != null) {
        ret.add(restoreObservableSnake(it, plInfo))
      } else {
        restoreSnake(it)
      }
    }
    state.state.foodsList.forEach {
      restoreApples(it)
    }
    
    return ret
  }
  
  override fun initGame(
    config: GameConfig, gamePlayerInfo: GamePlayerInfo
  ): List<ObservableSnakeEntity> {
    val sn = spawnNewSnake(gamePlayerInfo.playerId) ?: throw RuntimeException(
      "snake not found, да ебись ты в рот"
    )
    /** этот эксепшн не должен вылететь*/
    return listOf(sn);
  }
  
  @Synchronized
  override fun launch() {
    Logger.info { "${NetworkGameEngine::class.java.name} launched with config $gameConfig" }
    executorScope.gameDispatcher()
  }
  
  private fun restoreObservableSnake(
    snakeInfo: SnakesProto.GameState.Snake, playerInfo: SnakesProto.GamePlayer
  ): ObservableSnakeEntity {
    val sn = ObservableSnakeEntity(snakeInfo, playerInfo.score, this)
    entities.add(sn)
    return sn
  }
  
  private fun restoreSnake(snakeInfo: SnakesProto.GameState.Snake) {
    val sn = ObservableSnakeEntity(snakeInfo, 0, this)
    entities.add(sn)
  }
  
  private fun restoreApples(foodInfo: SnakesProto.GameState.Coord): AppleEntity {
    val apple = AppleEntity(foodInfo.x, foodInfo.y, this)
    entities.add(apple);
    return apple
  }
  
  override fun addSideEntity(entity: Entity) {
    sideEffectEntity.add(entity)
  }
  
  
  private fun checkUpdateApplesPostProcess() {
    val applesQ = entities.count { it.type == GameType.Apple }
    val playersQ = entities.count { it.type == GameType.Snake && it.alive }
    val targetQ = gameConfig.foodStatic + playersQ
    val diff = targetQ - applesQ
    if(diff > 0) {
      gameMap.findFreeCells(diff, GameType.Apple)?.forEach {
        sideEffectEntity.add(AppleEntity(it.x, it.y, this))
      }
    }
  }
  
  
  private fun sideEffectEntityPostProcess() {
    entities.addAll(sideEffectEntity)
    sideEffectEntity.clear()
  }
}