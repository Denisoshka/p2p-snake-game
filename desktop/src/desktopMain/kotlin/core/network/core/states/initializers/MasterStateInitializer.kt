package d.zhdanov.ccfit.nsu.core.network.core.states.initializers

import core.network.core.connection.Node
import core.network.core.connection.game.impl.ClusterNode
import core.network.core.connection.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.GameContext
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.SnakeEntity
import d.zhdanov.ccfit.nsu.core.game.engine.impl.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.ActiveObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.ObserverContext
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.states.MasterStateT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(
  MasterStateInitializer::class.java.name
)

object MasterStateInitializer {
  fun initMasterState(
    state: SnakesProto.GameMessage.StateMsg?,
    clusterNodesHandler: ClusterNodesHandler,
    stateMachine: NetworkStateHolder,
    netController: NetworkController,
    gamePlayerInfo: GamePlayerInfo,
    gameConfig: InternalGameConfig,
    initScope: CoroutineScope,
    joinPerUpdateQ: Int
  ): MasterStateT {
    val gameEngine: GameContext = GameEngine(
      joinPerUpdateQ, stateMachine, gameConfig.gameSettings
    )
    val nodesInit = state?.let {
      with(MasterStateInitializer) {
        initScope.restoreNodes(
          it, clusterNodesHandler, stateMachine
        )
      }
    }
    
    val entities = (state?.let {
      return@let gameEngine.initGame(
        gameConfig.gameSettings, gamePlayerInfo, it
      )
    } ?: run {
      return@run gameEngine.initGame(gameConfig.gameSettings, gamePlayerInfo)
    }).associateBy { it.id }
    
    val players = state?.state?.players?.playersList?.associateBy { it.id }
    runBlocking {
      nodesInit?.awaitAll()
    }?.filterNotNull()?.forEach {
      initObserver(players, it, entities)
    }
    
  }
  
  private fun initObserver(
    players: Map<Int, SnakesProto.GamePlayer>?,
    it: ClusterNode,
    entities: Map<Int, ActiveEntity>
  ) {
    val player = players?.get(it.nodeId)
    val entity = entities[it.nodeId]
    if(player != null) {
      if(it.nodeState == Node.NodeState.Active && entity != null) {
        it.apply {
          payload = ActiveObserverContext(
            it, player.name, entity as SnakeEntity
          )
        }
      } else if(it.nodeState < Node.NodeState.Disconnected) {
        it.apply {
          payload = ObserverContext(it, player.name)
          it.sendToNode()
        }
      }
    } else {
      it.shutdown()
      Logger.error { "player ${it.nodeId} not found" }
    }
  }
  
  private fun CoroutineScope.restoreNodes(
    state: SnakesProto.GameMessage.StateMsg,
    clusterNodesHandler: ClusterNodesHandler,
    stateMachine: NetworkStateHolder,
  ): List<Deferred<ClusterNode?>> {
    return state.state.players.playersList.filter {
      it.role != SnakesProto.NodeRole.MASTER && it.role != null
    }.map {
      async {
        try {
          val nodeState = when(it.role) {
            SnakesProto.NodeRole.NORMAL, SnakesProto.NodeRole.DEPUTY -> {
              Node.NodeState.Active
            }
            
            SnakesProto.NodeRole.VIEWER                              -> {
              Node.NodeState.Passive
            }
            
            SnakesProto.NodeRole.MASTER, null                        -> {
              return@async null
            }
          }
          return@async ClusterNode(
            nodeId = it.id,
            ipAddress = InetSocketAddress(it.ipAddress!!, it.port),
            payload = null,
            clusterNodesHandler = clusterNodesHandler,
            nodeState = nodeState
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
  
  fun disconnectNode() {
  
  }
}