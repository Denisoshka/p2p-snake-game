package d.zhdanov.ccfit.nsu.core.network.core

import core.network.core.connection.game.ClusterNodeT
import core.network.core.connection.game.impl.ClusterNode
import core.network.core.connection.game.impl.ClusterNodesHandler
import core.network.core.connection.lobby.impl.NetNodeHandler
import core.network.core.states.utils.ActiveStateInitializer
import core.network.core.states.utils.Utils
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.GameStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.NetworkStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.ActiveState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.LobbyState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.MasterState
import d.zhdanov.ccfit.nsu.core.network.core.states.initializers.PassiveStateInitializer
import d.zhdanov.ccfit.nsu.core.network.interfaces.GameSessionHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkStateContext
import d.zhdanov.ccfit.nsu.core.network.interfaces.StateConsumer
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.UnicastNetHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

private val Logger = KotlinLogging.logger(NetworkStateHolder::class.java.name)
private val kPortRange = 1..65535
private const val kChannelSize = 10

class NetworkStateHolder(
  private val netController: NetworkController,
  private val gameController: GameController,
  override val unicastNetHandler: UnicastNetHandler,
) : NetworkStateContext, StateConsumer, GameSessionHandler {
  
  private val stateContextDistacherScope = CoroutineScope(Dispatchers.Default)
  
  private val seqNumProvider = AtomicLong(0)
  private val stateNumProvider = AtomicInteger(0)
  private val nextNodeIdProvider = AtomicInteger(0)
  val nextSeqNum
    get() = seqNumProvider.incrementAndGet()
  val nextNodeId
    get() = nextNodeIdProvider.incrementAndGet()
  
  @Volatile var internalNodeId = 0
  
  private val nodeHandlers = NodeHandlers(
    netNodesHandler = NetNodeHandler(this),
    clusterNodesHandler = ClusterNodesHandler(TODO(), TODO(), TODO())
  )
  
  private val nodeChannels = NodeChannels(
    deadNodeChannel = Channel(kChannelSize),
    updateNodesInfo = Channel(kChannelSize),
    detachNodeChannel = Channel(kChannelSize),
    reconfigureContextChannel = Channel(kChannelSize)
  )
  
  override val networkState: NetworkStateT
    get() = networkStateHolder.get()
  
  private val networkStateHolder: AtomicReference<NetworkStateT> =
    AtomicReference(
      LobbyState(this, gameController, nodeHandlers.netNodesHandler)
    )
  
  private val latestGameStateHolder =
    AtomicReference<SnakesProto.GameMessage.StateMsg?>()
  val latestGameState: SnakesProto.GameMessage.StateMsg?
    get() = latestGameStateHolder.get()
  private val masterDeputyHolder: AtomicReference<Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?> =
    AtomicReference()
  val masterDeputy: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?
    get() = masterDeputyHolder.get()
  
  override fun handleJoinToGameReq(joinReq: Event.State.ByController.JoinReq) {
    when(val curState = networkState) {
      is LobbyState -> curState.requestJoinToGame(joinReq)
    }
  }
  
  override fun handleLaunchGame(
    launchGameReq: Event.State.ByController.LaunchGame
  ) {
    stateContextDistacherScope.launch {
      reconfigureContext(launchGameReq)
    }
  }
  
  override fun handleConnectToGame(joinReqAck: Event.State.ByInternal.JoinReqAck) {
    stateContextDistacherScope.launch {
      reconfigureContext(joinReqAck)
    }
  }
  
  override fun handleSwitchToLobby(
    switchToLobbyReq: Event.State.ByController.SwitchToLobby
  ) {
    stateContextDistacherScope.launch {
      reconfigureContext(switchToLobbyReq)
    }
  }
  
  override fun handleSendStateToController(state: SnakesProto.GameState) {
    gameController.acceptNewState(state)
  }
  
  override fun submitState(
    state: StateMsg,
    acceptedPlayers: List<Pair<Pair<ClusterNode, String>, ActiveEntity?>>
  ) {
    val st = this.networkStateHolder.get();
    if(st !is MasterState) return
    val msdp = masterDeputyHolder.get() ?: return
    
  }
  
  override fun cleanup() {
    TODO("Not yet implemented, da i naxyi nyzhno")
  }
  
  override fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  ) = netController.sendUnicast(msg, nodeAddress)
  
  
  override suspend fun detachNode(
    node: ClusterNodeT
  ) = nodeChannels.detachNodeChannel.send(node)
  
  override suspend fun terminateNode(
    node: ClusterNodeT
  ) = nodeChannels.deadNodeChannel.send(node)
  
  
  override suspend fun joinNode(node: ClusterNodeT) {
  
  }
  
  suspend fun reconfigureContext(event: Event.State) {
    nodeChannels.reconfigureContextChannel.send(event)
  }
  
  
  private suspend fun handleDetachNode(node: ClusterNodeT) {
    when(val st = networkState) {
      is GameStateT -> st.handleNodeDetach(node)
    }
  }
  
  /**
   * Мы меняем состояние кластера в одной функции так что исполнение линейно
   */
  private fun CoroutineScope.nodesNonLobbyWatcherRoutine(
    stateMachine: NetworkStateHolder
  ) = launch {
    try {
      Logger.info { "nodesNonLobbyWatcherRoutine launched" }
      while(isActive) {
        try {
          select {
            stateMachine.nodeChannels.detachNodeChannel.onReceive { node ->
              Logger.trace {
                "$node received in detachNodeChannel"
              }
              stateMachine.handleDetachNode(node)
            }
            
            stateMachine.nodeChannels.deadNodeChannel.onReceive { node ->
              Logger.trace {
                "$node received in deadNodeChannel"
              }
              stateMachine.handleDetachNode(node)
            }
            
            stateMachine.nodeChannels.updateNodesInfo.onReceive { node ->
              Logger.trace {
                "$node received in registerNewNodeChannel"
              }
            }
            
            stateMachine.nodeChannels.reconfigureContextChannel.onReceive { event ->
              Logger.trace {
                "$event received in registerNewNode"
              }
              when(event) {
                is Event.State.ByController.LaunchGame    -> {
                  launchGame(stateMachine, event)
                }
                
                is Event.State.ByInternal.JoinReqAck      -> {
                  Utils.onJoinGameAck(stateMachine, event)
                }
                
                is Event.State.ByController.SwitchToLobby -> {
                  switchToLobby(event)
                }
                
                is Event.State.ByInternal.MasterNow       -> {
                  switchToMaster(stateMachine, event)
                }
                
                else                                      -> {
                  Logger.error { "unsupported event by reconfigureContext $event" }
                }
              }
            }
            stateMachine.nodeChannels.
          }
        } catch(e: IllegalChangeStateAttempt) {
          Logger.error(e) { "during cluster state change" }
        }
      }
    } catch(_: CancellationException) {
      cancel()
    } catch(e: Exception) {
      Logger.error(e) { "unexpected error" }
    } finally {
      Logger.info { "nodesWatcherRoutine finished" }
    }
  }
  
  private fun joinAsActive(
    event: Event.State.ByInternal.JoinReqAck
  ) {
    when(val curState = networkState) {
      !is LobbyState -> IllegalChangeStateAttempt(
        fromState = curState.javaClass.name,
        toState = ActiveState::class.java.name,
      )
    }
    val destAddr = InetSocketAddress(
      event.onEventAck.gameAnnouncement.host,
      event.onEventAck.gameAnnouncement.port
    )
    apply {
      nodeHandlers.clusterNodesHandler.launch()
      val state = ActiveStateInitializer.createActiveState(
        nodeHandlers.clusterNodesHandler,
        this@NetworkStateHolder,
        destAddr,
        event.internalGameConfig,
        event.senderId,
        event.gamePlayerInfo.playerId
      )
      networkStateHolder.set(state)
    }
    Logger.trace { "joined as ${NodeRole.NORMAL} to $event" }
  }
  
  private fun joinAsViewer(
    event: Event.State.ByInternal.JoinReqAck
  ) {
    when(val curState = networkState) {
      !is LobbyState -> IllegalChangeStateAttempt(
        fromState = curState.javaClass.name,
        toState = ActiveState::class.java.name,
      )
    }
    val destAddr = InetSocketAddress(
      event.onEventAck.gameAnnouncement.host,
      event.onEventAck.gameAnnouncement.port
    )
    
    nodeHandlers.clusterNodesHandler.launch()
    
    PassiveStateInitializer.createActiveState(
      stateHolder = this,
      clusterNodesHandler = nodeHandlers.clusterNodesHandler,
      destAddr = destAddr,
      internalGameConfig = event.internalGameConfig,
      masterId = event.senderId,
      playerId = event.gamePlayerInfo.playerId,
    ).apply { networkStateHolder.set(this) }
    
    Logger.trace { "joined as ${NodeRole.VIEWER} to $event" }
  }
  
  private suspend fun launchGame(
    stateMachine: NetworkStateHolder,
    event: Event.State.ByController.LaunchGame,
  ) {
    val curState = stateMachine.networkStateHolder.get()
    if(curState !is LobbyState) throw throw IllegalChangeStateAttempt(
      fromState = curState.javaClass.name,
      toState = MasterState::class.java.name
    )
    
    val playerInfo = GamePlayerInfo(event.internalGameConfig.playerName, 0)
    
    stateMachine.apply {
      nodeHandlers.clusterNodesHandler.launch()
      networkStateHolder.set(
      
      )
    }
    gameController.openGameScreen()
  }
  
  fun switchToMaster(
    stateMachine: NetworkStateHolder, event: Event.State.ByInternal.MasterNow
  ) {
    val curState = stateMachine.networkStateHolder.get()
    if(curState !is ActiveState) throw IllegalChangeStateAttempt(
      fromState = curState.javaClass.name,
      toState = MasterState::class.java.name
    )
    
    stateMachine.apply {
      networkStateHolder.set(
        MasterState(
          stateHandler = this@apply,
          netController = netController,
          clusterNodesHandler = nodeHandlers.clusterNodesHandler,
          gamePlayerInfo = event.gamePlayerInfo,
          gameConfig = event.internalGameConfig,
          state = event.gameState,
        )
      )
    }
  }
  
  fun reconfigureMasterDeputy(
    masterDeputyInfo: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>
  ) {
    masterDeputyHolder.set(masterDeputyInfo)
  }
  
  suspend fun switchToLobby(
    event: Event.State.ByController.SwitchToLobby,
  ) {
    val curState = networkState
    if(curState is LobbyState) throw IllegalChangeStateAttempt(
      fromState = curState.javaClass.name, toState = LobbyState::class.java.name
    )
    
    masterDeputyHolder.set(null)
    curState.cleanup()
    nodeHandlers.clusterNodesHandler.shutdown()
    
    networkStateHolder.set(
      LobbyState(
        ncStateMachine = this,
        gameController = gameController,
        netNodesHandler = nodeHandlers.netNodesHandler,
      )
    )
    Logger.trace { "switchToLobby $event" }
    gameController.openLobby()
  }
  
  private suspend fun setupNewState(
    newStateMsg: SnakesProto.GameMessage.StateMsg
  ) {
    val oldState = latestGameState?.state ?: return
    val newState = newStateMsg.state
    if(oldState.stateOrder >= newState.stateOrder) return
    latestGameStateHolder.set(newStateMsg)
    val curMsDp = masterDeputy ?: return
    checkMsInfoInState(curMsDp, newState)
    checkDpInfoInState(curMsDp, newState)
    handleSendStateToController(newState)
  }
  
  private data class NodeChannels(
    val deadNodeChannel: Channel<ClusterNode>,
    val detachNodeChannel: Channel<ClusterNode>,
    val updateNodesInfo: Channel<Event.InternalGameEvent>,
    val reconfigureContextChannel: Channel<Event.State>
  )
  
  private data class NodeHandlers(
    val clusterNodesHandler: ClusterNodesHandler,
    val netNodesHandler: NetNodeHandler
  )
}
