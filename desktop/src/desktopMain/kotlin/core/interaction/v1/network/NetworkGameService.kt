package d.zhdanov.ccfit.nsu.core.interaction.v1.network

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.core.engine.GameContext
import d.zhdanov.ccfit.nsu.core.game.core.engine.impl.GameContextImpl
import d.zhdanov.ccfit.nsu.core.game.core.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.network.node.connected.StateHolder
import io.github.oshai.kotlinlogging.KotlinLogging
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
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.coroutines.cancellation.CancellationException

private val Logger = KotlinLogging.logger(NetworkGameContext::class.java.name)
private val SquareForSnakeSize = 5

class NetworkGameService(
  joinInStateQ: Int,
  private val gameConfig: GameConfig,
  private val idService: IdService,
  private val stateHolder: StateHolder,
) : NetworkGameContext {
  private val rwLock = ReentrantReadWriteLock()
  private val gameContext: GameContext = GameContextImpl(gameConfig)
  private val executorScope = CoroutineScope(
    Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  )
  private val joinBacklog =
    Channel<Pair<InetSocketAddress, SnakesProto.GameMessage>>(
      joinInStateQ
    )
  private val registeredPlayers: MutableMap<InetSocketAddress, Int> =
    hashMapOf()
  private val activePlayers: MutableMap<Int, PlayerContext> = hashMapOf()
  
  @Synchronized
  override fun launch() {
    executorScope.gameDispatcher()
  }
  
  @Synchronized
  override fun shutdown() {
    executorScope.cancel()
  }
  
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun CoroutineScope.gameDispatcher() = launch {
    try {
      var nextDelay = 0L
      while(isActive) {
        select {
          joinBacklog.onReceive {
            handleNewPlayer(it.first, it.second)
          }
          
          onTimeout(nextDelay) {
            val start = System.currentTimeMillis()
            gameContext.countNextStep()
            shootState()
            val end = System.currentTimeMillis()
            val diff = gameContext.gameConfig.stateDelayMs - (end - start)
            nextDelay = if(diff > 0) {
              diff
            } else {
              0
            }
          }
        }
      }
    } catch(e: CancellationException) {
      Logger.info { "game shutdown " }
      cancel()
    } finally {
      Logger.info { "game finished " }
    }
  }
  
  private fun shootState() {
    rwLock.read {
    
    }
    registeredPlayers.forEach { (ip, id) ->
    
    }
  }
  
  private fun handleNewPlayer(
    ipAddr: InetSocketAddress, initMessage: SnakesProto.GameMessage,
  ) {
    if(initMessage.typeCase != SnakesProto.GameMessage.TypeCase.JOIN) return
    
    registeredPlayers[ipAddr]?.let { playerId ->
      stateHolder.submitNewRegisteredPlayer(ipAddr, initMessage, playerId)
      return@let
    }
    
    val joinReq = initMessage.join
    val id = if(joinReq.requestedRole != SnakesProto.NodeRole.VIEWER) {
      addActivePlayer(ipAddr)
    } else {
      addPassivePlayer(ipAddr)
    }
    
    stateHolder.submitNewRegisteredPlayer(ipAddr, initMessage, id)
  }
  
  override fun submitNewPlayer(
    playerInfo: Pair<InetSocketAddress, SnakesProto.GameMessage>
  ) {
    if(!joinBacklog.trySend(playerInfo).isSuccess) {
      throw NetworkGameContextException("retry join later")
    }
  }
  
  override fun submitSteerMsq(
    playerId: Int, msg: SnakesProto.GameMessage.SteerMsg, seq: Long
  ): Boolean {
    rwLock.read {
      activePlayers[playerId]?.let {
        return it.handleSteer(msg, seq)
      }
    }
    return false
  }
  
  private fun addActivePlayer(ipAddr: InetSocketAddress): Int? {
    rwLock.write {
      val (startX, startY) = gameContext.gameMap.findFreeSquare(
        SquareForSnakeSize
      ) ?: return null
      val id = idService.nextId
      val sn = gameContext.addSnake(id, startX, startY)
      activePlayers[id] = PlayerContext(sn, ipAddr)
      registeredPlayers[ipAddr] = id
      return id
    }
  }
  
  private fun addPassivePlayer(ipAddr: InetSocketAddress): Int {
    rwLock.write {
      val id = idService.nextId
      registeredPlayers[ipAddr] = id
      return id
    }
  }
  
  private class PlayerContext(
    val entity: ActiveEntity, val ipAddr: InetSocketAddress
  ) {
    var lastUpdate: Long = 0
    fun handleSteer(msg: SnakesProto.GameMessage.SteerMsg, seq: Long): Boolean {
      synchronized(this) {
        if(lastUpdate >= seq) return false
        entity.changeState(Direction.fromProto(msg.direction))
        lastUpdate = seq
      }
      return true
    }
  }
}