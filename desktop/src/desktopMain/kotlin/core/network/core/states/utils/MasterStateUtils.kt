package core.network.core.states.utils

import core.network.core.connection.Node
import core.network.core.connection.game.impl.ClusterNode
import core.network.core.connection.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.GameContext
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.SnakeEntity
import d.zhdanov.ccfit.nsu.core.game.engine.impl.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.LocalObserverContext
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalMasterLaunchAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.MasterState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(
  MasterStateUtils::class.java.name
)

object MasterStateUtils {
  const val JoinPerUpdateQ: Int = 10
  
  private fun getScope() = CoroutineScope(Dispatchers.Default)
  
  fun prepareMasterContext(
    gameConfig: InternalGameConfig,
    gamePlayerInfo: GamePlayerInfo,
    stateHolder: NetworkStateHolder,
    clusterNodesHandler: ClusterNodesHandler,
  ): MasterState {
    val eng = GameEngine(JoinPerUpdateQ, stateHolder, gameConfig.gameSettings)
    val entities = init(
      eng, gameConfig, gamePlayerInfo
    )
    val player = createLocalObserverContext(
      entities, gamePlayerInfo, stateHolder
    )
    val scope = getScope()
    try {
      Logger.info { "master inited" }
      return MasterState(
        internalGameConfig = gameConfig,
        gameEngine = eng,
        stateHolder = stateHolder,
        nodesHolder = clusterNodesHandler,
        gamePlayerInfo = gamePlayerInfo,
        player = player,
        nodesInitScope = scope
      )
    } catch(e: Exception) {
      scope.cancel()
      throw e
    }
  }
  
  fun prepareMasterFromState(
    state: SnakesProto.GameMessage.StateMsg,
    clusterNodesHandler: ClusterNodesHandler,
    gameConfig: InternalGameConfig,
    gamePlayerInfo: GamePlayerInfo,
    stateHolder: NetworkStateHolder,
  ): MasterState {
    val eng = GameEngine(JoinPerUpdateQ, stateHolder, gameConfig.gameSettings)
    val scope = getScope()
    try {
      val entities = initFromState(
        gameEngine = eng,
        gameConfig = gameConfig,
        gamePlayerInfo = gamePlayerInfo,
        clusterNodesHandler = clusterNodesHandler,
        initScope = scope,
        state = state
      )
      val player = createLocalObserverContext(
        entities, gamePlayerInfo, stateHolder
      )
      Logger.info { "master inited" }
      return MasterState(
        internalGameConfig = gameConfig,
        gameEngine = eng,
        stateHolder = stateHolder,
        nodesHolder = clusterNodesHandler,
        gamePlayerInfo = gamePlayerInfo,
        player = player,
        nodesInitScope = scope
      )
    } catch(e: Exception) {
      scope.cancel()
      throw e
    }
  }
  
  private fun createLocalObserverContext(
    entities: Map<Int, ActiveEntity>,
    gamePlayerInfo: GamePlayerInfo,
    stateHolder: NetworkStateHolder
  ): LocalObserverContext {
    val localSnake = entities[gamePlayerInfo.playerId]
      ?: throw IllegalMasterLaunchAttempt("local snake absent in state message")
    val player = createLocalObserverContext(
      gamePlayerInfo, localSnake, stateHolder
    )
    return player
  }
  
  private fun initNodes(
    state: SnakesProto.GameMessage.StateMsg,
    initScope: CoroutineScope,
    clusterNodesHandler: ClusterNodesHandler,
  ) = state.let {
    with(MasterStateUtils) {
      initScope.restoreNodes(
        it, clusterNodesHandler
      )
    }
  }
  
  
  private fun init(
    gameEngine: GameContext,
    gameConfig: InternalGameConfig,
    gamePlayerInfo: GamePlayerInfo,
  ) = gameEngine.initGame(
    gameConfig.gameSettings, gamePlayerInfo
  ).associateBy { it.id }
  
  private fun initFromState(
    gameEngine: GameContext,
    gameConfig: InternalGameConfig,
    gamePlayerInfo: GamePlayerInfo,
    clusterNodesHandler: ClusterNodesHandler,
    initScope: CoroutineScope,
    state: SnakesProto.GameMessage.StateMsg,
  ): Map<Int, ActiveEntity> {
    val nodes = initNodes(state, initScope, clusterNodesHandler)
    val ret = gameEngine.initGame(
      gameConfig.gameSettings, gamePlayerInfo, state
    ).associateBy { it.id }
    initObservers(state, nodes, ret)
    return ret
  }
  
  private fun initObservers(
    state: SnakesProto.GameMessage.StateMsg,
    nodesInit: List<Deferred<ClusterNode?>>,
    entities: Map<Int, ActiveEntity>,
  ) {
    val players = state.state.players.playersList.associateBy { it.id }
    runBlocking {
      nodesInit.awaitAll()
    }.filterNotNull().forEach {
      initObservers(players, it, entities)
    }
  }
  
  
  private fun initObservers(
    players: Map<Int, SnakesProto.GamePlayer>,
    it: ClusterNode,
    entities: Map<Int, ActiveEntity>
  ) {
    val player = players[it.nodeId]
    val entity = entities[it.nodeId]
    if(player != null && it.nodeState == Node.NodeState.Active && entity != null) {
      TODO("необходимо добавить возможность добавить наблюдателя")
    } else if(player != null && it.nodeState == Node.NodeState.Passive && entity == null) {/*ничего не делаем*/
    } else {
      it.shutdown()
      /**
       * Вообще такой ситуации быть не должно тк все состояние снимается с
       * контекста наблюдателя
       */
      Logger.error { "player ${it.nodeId} not found" }
    }
  }
  
  private fun createLocalObserverContext(
    gamePlayerInfo: GamePlayerInfo,
    localSnake: ActiveEntity,
    stateMachine: NetworkStateHolder
  ): LocalObserverContext {
    val player = LocalObserverContext(
      name = gamePlayerInfo.playerName,
      snake = localSnake as SnakeEntity,
      lastUpdateSeq = 0,
      ncStateMachine = stateMachine,
      score = localSnake.score,
    )
    return player
  }
  
  private fun CoroutineScope.restoreNodes(
    state: SnakesProto.GameMessage.StateMsg,
    clusterNodesHandler: ClusterNodesHandler,
  ): List<Deferred<ClusterNode?>> {
    return state.state.players.playersList.filter {
      it.role != SnakesProto.NodeRole.MASTER && it.role != null
    }.map {
      async {
        try {
          val nodeState = when(it.role) {
            SnakesProto.NodeRole.NORMAL, SnakesProto.NodeRole.DEPUTY -> {
              Node.NodeState.Passive
            }
            
            SnakesProto.NodeRole.VIEWER                              -> {
              Node.NodeState.Active
            }
            
            SnakesProto.NodeRole.MASTER, null                        -> {
              return@async null
            }
          }
          
          return@async ClusterNode(
            nodeId = it.id,
            ipAddress = InetSocketAddress(it.ipAddress!!, it.port),
            clusterNodesHandler = clusterNodesHandler,
            nodeState = nodeState,
            name = it.name
          ).apply {
            clusterNodesHandler.registerNode(this)
          }
        } catch(e: Exception) {
          Logger.error(e) {
            "during restore node ${it.name} ip: ${it.ipAddress} port: ${it.port}"
          }
          return@async null
        }
      }
    }
  }
}