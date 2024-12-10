package d.zhdanov.ccfit.nsu.core.network.core

import core.network.core.connection.game.ClusterNodeT
import core.network.core.connection.game.impl.ClusterNode
import core.network.core.connection.game.impl.ClusterNodesHandler
import core.network.core.connection.lobby.impl.NetNodeHandler
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.GameStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.NetworkStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.ActiveState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.LobbyState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.MasterState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.PassiveState
import core.network.core.connection.Node
import d.zhdanov.ccfit.nsu.core.network.interfaces.GameSessionHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkStateContext
import d.zhdanov.ccfit.nsu.core.network.interfaces.StateConsumer
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
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

private val Logger = KotlinLogging.logger(NetworkStateMachine::class.java.name)
private val kPortRange = 1..65535
private const val kChannelSize = 10

class NetworkStateMachine(
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
  
  @Volatile private var internalNodeId = 0
  
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
      LobbyState(this, netController, nodeHandlers.netNodesHandler)
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
  
  override fun handleConnectToGame() {
    stateContextDistacherScope.launch {
      reconfigureContext()
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
    
    val (ms, dp) = msdp
    st.player.shootContextState(state, ms, dp)
    
    for((_, node) in nodeHandlers.clusterNodesHandler) {
      node.payload?.shootContextState(state, ms, dp)
    }
    
    val stateNum = stateNumProvider.incrementAndGet()
    state.stateOrder = stateNum
    
    val p2pmsg = GameMessage(nextSeqNum, state)
    val protomsg = toGameMessage(p2pmsg, MessageType.StateMsg)
    
    for((ipAddr, node) in nodeHandlers.clusterNodesHandler) {
      if(!node.running) continue
      node.addMessageForAck(protomsg)
      sendUnicast(protomsg, ipAddr)
    }
  }
  
  override fun cleanup() {
    TODO("Not yet implemented, da i naxyi nyzhno")
  }
  
  override fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  ) = netController.sendUnicast(msg, nodeAddress)
  
  
  /**
   * @return `Node<MessageT, InboundMessageTranslatorT, PayloadT>` if new
   * deputy was chosen successfully, else `null`
   * @throws IllegalChangeStateAttempt
   */
  fun chooseSetNewDeputy(oldDeputyId: Int): ClusterNodeT? {
    val (masterInfo, _) = masterDeputyHolder.get()
      ?: throw IllegalChangeStateAttempt(
        "current master deputy missing "
      )
    
    val deputyCandidate = nodeHandlers.clusterNodesHandler.find {
      it.value.nodeState == Node.NodeState.Active && it.value.payload != null && it.value.nodeId != oldDeputyId
    }?.value
    
    val newDeputyInfo = deputyCandidate?.let {
      Pair(it.ipAddress, it.nodeId)
    }
    
    masterDeputyHolder.set(Pair(masterInfo, newDeputyInfo))
    
    return deputyCandidate
  }
  
  override suspend fun detachNode(
    node: ClusterNodeT
  ) = nodeChannels.detachNodeChannel.send(node)
  
  override suspend fun terminateNode(
    node: ClusterNodeT
  ) = nodeChannels.deadNodeChannel.send(node)
  
  
  override suspend fun joinNode(node: ClusterNodeT) {
  
  }
  
  fun onPingMsg(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage, nodeId: Int
  ) {
    nodeHandlers.clusterNodesHandler[ipAddress]?.let {
      val ack = MessageUtils.MessageProducer.getAckMsg(
        message.msgSeq, nodeId, it.nodeId
      )
      netController.sendUnicast(ack, it.ipAddress)
    }
  }
  
  fun nonLobbyOnAck(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    nodeHandlers.clusterNodesHandler[ipAddress]?.ackMessage(message)
  }
  
  fun onStateMsg(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    val (ms, _) = masterDeputyHolder.get() ?: return
    if(ms.first != ipAddress) return
    val stateSeq = message.state.state.stateOrder
    
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
    stateMachine: NetworkStateMachine
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
                  onJoinGameAck(stateMachine, event)
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
  
  private suspend fun onJoinGameAck(
    stateMachine: NetworkStateMachine, event: Event.State.ByInternal.JoinReqAck
  ) {
    Logger.trace { "join to game with $event" }
    when(event.onEventAck.playerRole) {
      NodeRole.VIEWER -> {
        joinAsViewer(event)
      }
      
      NodeRole.NORMAL -> {
        joinAsActive(event)
      }
      
      else            -> {
        Logger.error { "incorrect $event" }
        throw IllegalChangeStateAttempt("incorrect $event")
      }
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
      val masterNode = ClusterNode(
        nodeState = Node.NodeState.Active,
        nodeId = event.senderId,
        ipAddress = destAddr,
        payload = null,
        clusterNodesHandler = nodeHandlers.clusterNodesHandler
      )
      nodeHandlers.clusterNodesHandler.registerNode(masterNode)
      networkStateHolder.set(
        ActiveState(
          gameConfig = event.internalGameConfig,
          stateMachine = this@apply,
          controller = netController,
          clusterNodesHandler = nodeHandlers.clusterNodesHandler,
          nodeId = event.gamePlayerInfo.playerId
        )
      )
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
    val masterNode = ClusterNode(
      nodeState = Node.NodeState.Active,
      nodeId = event.senderId,
      ipAddress = destAddr,
      payload = null,
      clusterNodesHandler = nodeHandlers.clusterNodesHandler
    )
    nodeHandlers.clusterNodesHandler.registerNode(masterNode)
    networkStateHolder.set(
      PassiveState(
        gameConfig = event.internalGameConfig,
        stateMachine = this,
        controller = netController,
        clusterNodesHandler = nodeHandlers.clusterNodesHandler,
        nodeId = event.gamePlayerInfo.playerId
      )
    )
    Logger.trace { "joined as ${NodeRole.VIEWER} to $event" }
  }
  
  private suspend fun launchGame(
    stateMachine: NetworkStateMachine,
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
        MasterState(
          stateMachine = this,
          netController = netController,
          clusterNodesHandler = nodeHandlers.clusterNodesHandler,
          gameConfig = event.internalGameConfig,
          gamePlayerInfo = playerInfo,
          state = null,
        )
      )
    }
  }
  
  fun switchToMaster(
    stateMachine: NetworkStateMachine, event: Event.State.ByInternal.MasterNow
  ) {
    val curState = stateMachine.networkStateHolder.get()
    if(curState !is ActiveState) throw IllegalChangeStateAttempt(
      fromState = curState.javaClass.name,
      toState = MasterState::class.java.name
    )
    
    stateMachine.apply {
      networkStateHolder.set(
        MasterState(
          stateMachine = this@apply,
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
        controller = netController,
        netNodesHandler = nodeHandlers.netNodesHandler,
      )
    )
    Logger.trace { "switchToLobby $event" }
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
  
  private suspend fun checkMsInfoInState(
    curMsDp: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>,
    state: SnakesProto.GameState
  ) {
    val stateMs = state.players.playersList.find {
      it.role == SnakesProto.NodeRole.MASTER
    }
    if(stateMs == null) {
      Logger.trace { "master absent in state $state" }
      switchToLobby(Event.State.ByController.SwitchToLobby)
      return
    }
  }
  
  private suspend fun checkDpInfoInState(
    curMsDp: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>,
    state: SnakesProto.GameState
  ) {
    val (curMs, curDp) = curMsDp
    val stateDp = state.players.playersList.find {
      it.role == SnakesProto.NodeRole.DEPUTY
    }
    if(stateDp == null) {
      Logger.trace { "deputy absent in state $state" }
      masterDeputyHolder.set(curMs to null)
    } else if(stateDp.id != curDp?.second) {
      try {
        Logger.trace {
          "setup new deputy (id:${stateDp.id}, addr:${stateDp.ipAddress}, port:${stateDp.id})"
        }
        val ipAddr = InetSocketAddress(stateDp.ipAddress, stateDp.port)
        masterDeputyHolder.set(curMs to (ipAddr to stateDp.id))
      } catch(e: Exception) {
        Logger.error(e) { "during setup new deputy (id:${stateDp.id}, addr:${stateDp.ipAddress}, port:${stateDp.id})" }
        switchToLobby(Event.State.ByController.SwitchToLobby)
      }
    }
  }
  
  private data class NodeChannels(
    val deadNodeChannel: Channel<ClusterNodeT>,
    val detachNodeChannel: Channel<ClusterNodeT>,
    val updateNodesInfo: Channel<Event.InternalGameEvent>,
    val reconfigureContextChannel: Channel<Event.State>
  )
  
  private data class NodeHandlers(
    val clusterNodesHandler: ClusterNodesHandler,
    val netNodesHandler: NetNodeHandler
  )
}
