package d.zhdanov.ccfit.nsu.core.network.core

import core.network.core.connection.game.impl.ClusterNode
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.controllers.dto.GameAnnouncement
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeDestination
import d.zhdanov.ccfit.nsu.core.network.core.states.ActiveStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.NetworkStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.events.StateEvent
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.ActiveState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.LobbyState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.MasterState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.PassiveState
import d.zhdanov.ccfit.nsu.core.network.core.states.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.network.core.states.node.lobby.impl.NetNodeHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkStateContext
import d.zhdanov.ccfit.nsu.core.network.interfaces.StateConsumer
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
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

class NetworkStateMachine(
  private val netController: NetworkController,
  private val gameController: GameController,
) : NetworkStateContext, StateConsumer {
  private val seqNumProvider = AtomicLong(0)
  private val stateNumProvider = AtomicInteger(0)
  private val nextNodeIdHolder = AtomicInteger(0)
  private val clusterNodesHandler: ClusterNodesHandler = TODO()
  private val netNodesHandler: NetNodeHandler = TODO()
  private val contextScope: CoroutineScope
  private val deadNodeChannel = Channel<ClusterNodeT>(TODO())
  private val registerNewNode = Channel<ClusterNodeT>(TODO())
  private val detachNodeChannel = Channel<ClusterNodeT>(TODO())
  private val reconfigureContext =
    Channel<Pair<StateEvent, ClusterNodeT?>>(TODO())
  val nextSeqNum
    get() = seqNumProvider.incrementAndGet()
  
  @Volatile var nodeId = 0
    private set
  
  val nextNodeId
    get() = nextNodeIdHolder.incrementAndGet()
  
  override val networkState: NetworkStateT
    get() = networkStateHolder.get()
  
  private val networkStateHolder: AtomicReference<NetworkStateT> =
    AtomicReference(
      LobbyState(this, netController, netNodesHandler)
    )
  
  private val latestGameState = AtomicReference<Pair<StateMsg, Int>?>()
  private val masterDeputyHolder: AtomicReference<Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?> =
    AtomicReference()
  val masterDeputy: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?
    get() = masterDeputyHolder.get()
  
  override
  
  fun submitState(
    state: StateMsg,
    acceptedPlayers: List<Pair<Pair<ClusterNode, String>, ActiveEntity?>>
  ) {
    val st = this.networkStateHolder.get();
    if(st !is MasterState) return
    val msdp = masterDeputyHolder.get() ?: return
    
    val (ms, dp) = msdp
    st.player.shootContextState(state, ms, dp)
    
    for((_, node) in clusterNodesHandler) {
      node.payload?.shootContextState(state, ms, dp)
    }
    
    val stateNum = stateNumProvider.incrementAndGet()
    state.stateOrder = stateNum
    
    val p2pmsg = GameMessage(nextSeqNum, state)
    val protomsg = MessageTranslator.toGameMessage(p2pmsg, MessageType.StateMsg)
    
    for((ipAddr, node) in clusterNodesHandler) {
      if(!node.running) continue
      
      node.addMessageForAck(protomsg)
      sendUnicast(protomsg, ipAddr)
    }
  }
  
  override fun joinToGame(announcement: GameAnnouncement) {
    TODO("Not yet implemented")
  }
  
  override fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  ) = netController.sendUnicast(msg, nodeAddress)
  
  override fun cleanup() {
    TODO("Not yet implemented")
  }
  
  /**
   * @return `Node<MessageT, InboundMessageTranslatorT, PayloadT>` if new
   * deputy was chosen successfully, else `null`
   * @throws IllegalChangeStateAttempt
   */
  private fun chooseSetNewDeputy(oldDeputyId: Int): ClusterNodeT? {
    val (masterInfo, _) = masterDeputyHolder.get()
      ?: throw IllegalChangeStateAttempt(
        "current master deputy missing "
      )
    
    val deputyCandidate = clusterNodesHandler.find {
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
  ) = detachNodeChannel.send(node)
  
  override suspend fun terminateNode(
    node: ClusterNodeT
  ) = deadNodeChannel.send(node)
  
  private suspend fun handleDetachNode(node: ClusterNodeT) {
    when(val st = networkState) {
      is MasterState  -> masterHandleNodeDetach(st, node)
      is ActiveState  -> activeHandleNodeDetach(st, node)
      is PassiveState -> passiveHandleNodeDetach(st, node)
    }
  }
  
  
  /**
   * @throws IllegalChangeStateAttempt
   * */
  private suspend fun passiveHandleNodeDetach(
    st: PassiveState, node: ClusterNodeT
  ) {
    val (msInfo, depInfo) = masterDeputyHolder.get() ?: return
    if(msInfo.second != node.nodeId) throw IllegalChangeStateAttempt(
      "non master node $node in passiveHandleNodeDetach"
    )
    
    if(depInfo == null) {
      switchToLobby(StateEvent.ControllerEvent.SwitchToLobby)
    } else {
      normalChangeInfoDeputyToMaster(depInfo, node)
    }
  }
  
  private suspend fun activeHandleNodeDetach(
    st: ActiveStateT, node: Node
  ) {
    val (msInfo, depInfo) = masterDeputyHolder.get() ?: return
    if(depInfo == null) {
      Logger.warn { "activeHandleNodeDetach depInfo absent" }
      
      switchToLobby(StateEvent.ControllerEvent.SwitchToLobby)
      return
    }
    
    if(node.nodeId == msInfo.second && nodeId == depInfo.second && node.ipAddress == msInfo.first) {
      activeDeputyHandleMasterDetach(st, depInfo)
    } else if(node.nodeId == msInfo.second && nodeId != depInfo.second && node.ipAddress == msInfo.first) {
      normalChangeInfoDeputyToMaster(depInfo, node)
    } else {
      throw IllegalChangeStateAttempt(
        "non master $node try to detach from cluster in state $networkState"
      )
    }
  }
  
  private suspend fun masterHandleNodeDetach(
    st: MasterState, node: ClusterNodeT
  ) {
    val (_, depInfo) = masterDeputyHolder.get() ?: return
    if(node.nodeId != depInfo?.second && node.ipAddress == depInfo?.first) return
    
    val newDep = chooseSetNewDeputy(node.nodeId) ?: return
    
    /**
     * choose new deputy
     */
    
    val outMsg = MessageUtils.MessageProducer.getRoleChangeMsg(
      msgSeq = nextSeqNum,
      senderId = nodeId,
      receiverId = newDep.nodeId,
      senderRole = SnakesProto.NodeRole.MASTER,
      receiverRole = SnakesProto.NodeRole.DEPUTY
    )
    
    netController.sendUnicast(outMsg, newDep.ipAddress)
  }
  
  
  private suspend fun activeDeputyHandleMasterDetach(
    st: ActiveStateT, depInfo: Pair<InetSocketAddress, Int>
  ) {
    when(val state = latestGameState.get()) {
      null -> {
        Logger.warn {
          "during activeDeputyHandleMasterDetach from :${
            ActiveState::class
          } to ${
            MasterState::class
          } latestGameState is null"
        }
        switchToLobby(StateEvent.ControllerEvent.SwitchToLobby)
      }
      
      else -> {
        masterDeputyHolder.set(depInfo to null)
        
        val config = st.gameConfig
        val plInfo = GamePlayer(
          config.playerName,
          nodeId,
          null,
          null,
          NodeRole.MASTER,
          PlayerType.HUMAN,
          0
        )
        Logger.info {
          "activeDeputyHandleMasterDetach MasterNow config: $config player: $plInfo"
        }
        
        switchToMaster(
          StateEvent.InternalEvent.MasterNow(
            gameState = state.first,
            gamePlayerInfo = plInfo,
            internalGameConfig = config,
          )
        )
      }
    }
  }
  
  private fun normalChangeInfoDeputyToMaster(
    depInfo: Pair<InetSocketAddress, Int>, masterNode: Node
  ) {
    masterDeputyHolder.set(Pair(depInfo, null))
    val unacknowledgedMessages = masterNode.getUnacknowledgedMessages()
    val newMasterClusterNode = ClusterNode(
      nodeState = Node.NodeState.Active,
      nodeId = depInfo.second,
      ipAddress = depInfo.first,
      payload = null,
      clusterNodesHandler = clusterNodesHandler
    )
    clusterNodesHandler.registerNode(newMasterClusterNode)
    masterNode.addAllMessageForAck(unacknowledgedMessages)
  }
  
  /**
   * @throws IllegalNodeDestination
   * */
  private fun nonMasterParseState(state: StateMsg) {
    val depStateInfo = state.players.find { it.nodeRole == NodeRole.DEPUTY }
    val (msInfo, depInfo) = masterDeputyHolder.get() ?: return
    if(depInfo?.second == depStateInfo?.id) return
    val newDepInfo = depStateInfo?.let {
      try {
        Pair(InetSocketAddress(it.ipAddress!!, it.port!!), it.id)
      } catch(e: Exception) {
        Logger.error(e) { "deputy destination has dirty info" }
        throw IllegalNodeDestination(e)
      }
    }
    masterDeputyHolder.set(Pair(msInfo, newDepInfo))
  }
  
  fun onPingMsg(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    clusterNodesHandler[ipAddress]?.let {
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
    clusterNodesHandler[ipAddress]?.ackMessage(message)
  }
  
  fun onStateMsg(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    val (ms, _) = masterDeputyHolder.get() ?: return
    if(ms.first != ipAddress) return
    
    if(setupNewState(message.state.state.stateOrder, message.state)) {
      val p2pMsg = MessageTranslator.fromProto(message, MessageType.StateMsg)
      nonMasterParseState(p2pMsg.msg)
    }
  }
  
  private fun setupNewState(
    newStateOrder: Int, stateMsg: SnakesProto.GameMessage.StateMsg
  ): Boolean {
    while(true) {
      val curState = latestGameState.get() ?: return true
      if(newStateOrder <= curState.second) {
        return false
      }
      
      val newState = stateMsg to newStateOrder
      if(latestGameState.compareAndSet(curState, newState)) {
        return true
      }
    }
  }
  
  private fun handleJoin(event: StateEvent.ControllerEvent.JoinReq) {
    val state = networkStateHolder.get()
    if(state is LobbyState) {
      state.sendJoinMsg(event)
    }
  }
  
  
  private suspend fun switchToLobbyGracefully(
    event: StateEvent.ControllerEvent.SwitchToLobby
  ) {
    val state = networkState
    if(state is LobbyState) return
    
    if(state is MasterState) {
      val (ms, dp)
    } else if(state is ActiveState || state is PassiveState) {
      
    }
  }
  
  
  private fun handleJoinAck(event: StateEvent.InternalEvent.JoinReqAck) {
    val curState = networkStateHolder.get()
    if(curState !is LobbyState) {
      throw IllegalChangeStateAttempt(
        fromState = curState.javaClass.name,
        toState = MasterState::class.java.name
      )
    }
    TODO()
  }
  
  
  private companion object StateChangeBoss {
    /**
     * Мы меняем состояние кластера в одной функции так что исполнение линейно
     */
    fun CoroutineScope.nodesNonLobbyWatcherRoutine(
      stateMachine: NetworkStateMachine
    ) = launch {
      try {
        Logger.info { "nodesNonLobbyWatcherRoutine launched" }
        while(isActive) {
          try {
            select {
              stateMachine.detachNodeChannel.onReceive { node ->
                Logger.trace {
                  "$node received in detachNodeChannel ${stateMachine.deadNodeChannel}"
                }
                stateMachine.handleDetachNode(node)
              }
              
              stateMachine.deadNodeChannel.onReceive { node ->
                Logger.trace {
                  "$node received in deadNodeChannel ${stateMachine.deadNodeChannel}"
                }
                stateMachine.handleDetachNode(node)
              }
              
              stateMachine.registerNewNode.onReceive { node ->
                Logger.trace {
                  "$node received in registerNewNode ${stateMachine.registerNewNode}"
                }
                stateMachine.handleNodeRegister(node)
              }
              
              stateMachine.reconfigureContext.onReceive { (event, node) ->
                Logger.trace {
                  "$node $event received in registerNewNode ${stateMachine.registerNewNode}"
                }
                when(event) {
                  is StateEvent.ControllerEvent.LaunchGame    -> {
                    launchGame(stateMachine, event)
                  }
                  
                  is StateEvent.InternalEvent.JoinReqAck      -> {
                    joinGame(stateMachine, event)
                  }
                  
                  is StateEvent.ControllerEvent.SwitchToLobby -> {
                    switchToLobby(stateMachine, event)
                  }
                  
                  is StateEvent.InternalEvent.MasterNow       -> {
                    switchToMaster(stateMachine, event)
                  }
                  
                  else                                        -> {
                    Logger.error { "unsupported event by reconfigureContext $event" }
                  }
                }
              }
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
    
    private suspend fun joinGame(
      stateMachine: NetworkStateMachine,
      event: StateEvent.InternalEvent.JoinReqAck
    ) {
      if(event.gamePlayerInfo.nodeRole == NodeRole.VIEWER) {
        joinAsViewer(stateMachine, event)
      } else {
        joinAsActive(stateMachine, event)
      }
    }
    
    private fun joinAsActive(
      stateMachine: NetworkStateMachine,
      event: StateEvent.InternalEvent.JoinReqAck
    ) {
      when(val curState = stateMachine.networkState) {
        !is LobbyState -> IllegalChangeStateAttempt(
          fromState = curState.javaClass.name,
          toState = ActiveState::class.java.name,
          msg = "in ${this::joinAsViewer.name}"
        )
      }
      val destAddr = InetSocketAddress(
        event.onEventAck.gameAnnouncement.host,
        event.onEventAck.gameAnnouncement.port
      )
      stateMachine.apply {
        clusterNodesHandler.launch()
        val masterNode = ClusterNode(
          nodeState = Node.NodeState.Active,
          nodeId = event.senderId,
          ipAddress = destAddr,
          payload = null,
          clusterNodesHandler = clusterNodesHandler
        )
        clusterNodesHandler.registerNode(masterNode)
        networkStateHolder.set(
          ActiveState(
            gameConfig = event.internalGameConfig,
            stateMachine = this@apply,
            controller = netController,
            clusterNodesHandler = clusterNodesHandler
          )
        )
      }
    }
    
    private fun joinAsViewer(
      stateMachine: NetworkStateMachine,
      event: StateEvent.InternalEvent.JoinReqAck
    ) {
      when(val curState = stateMachine.networkState) {
        !is LobbyState -> IllegalChangeStateAttempt(
          fromState = curState.javaClass.name,
          toState = ActiveState::class.java.name,
          msg = "in ${this::joinAsViewer.name}"
        )
      }
      val destAddr = InetSocketAddress(
        event.onEventAck.gameAnnouncement.host,
        event.onEventAck.gameAnnouncement.port
      )
      stateMachine.apply {
        clusterNodesHandler.launch()
        val masterNode = ClusterNode(
          nodeState = Node.NodeState.Active,
          nodeId = event.senderId,
          ipAddress = destAddr,
          payload = null,
          clusterNodesHandler = clusterNodesHandler
        )
        clusterNodesHandler.registerNode(masterNode)
        networkStateHolder.set(
          PassiveState(
            gameConfig = event.internalGameConfig,
            ncStateMachine = this@apply,
            controller = netController,
            clusterNodesHandler = clusterNodesHandler
          )
        )
      }
      Logger.info { "joined as ${NodeRole.VIEWER} to ${event.onEventAck.gameAnnouncement}" }
    }
    
    private suspend fun launchGame(
      stateMachine: NetworkStateMachine,
      event: StateEvent.ControllerEvent.LaunchGame,
    ) {
      val curState = stateMachine.networkStateHolder.get()
      if(curState !is LobbyState) throw throw IllegalChangeStateAttempt(
        fromState = curState.javaClass.name,
        toState = MasterState::class.java.name
      )
      
      val plInfo = GamePlayer(
        name = event.internalGameConfig.playerName,
        id = stateMachine.nodeId,
        nodeRole = NodeRole.MASTER,
        playerType = PlayerType.HUMAN,
        score = 0,
        ipAddress = null,
        port = null,
      )
      
      stateMachine.apply {
        networkStateHolder.set(
          MasterState(
            ncStateMachine = this,
            netController = netController,
            clusterNodesHandler = clusterNodesHandler,
            gameConfig = event.internalGameConfig,
            playerInfo = plInfo,
            state = null
          )
        )
      }
    }
    
    private suspend fun switchToMaster(
      stateMachine: NetworkStateMachine,
      event: StateEvent.InternalEvent.MasterNow
    ) {
      val curState = stateMachine.networkStateHolder.get()
      if(curState !is ActiveState) throw IllegalChangeStateAttempt(
        fromState = curState.javaClass.name,
        toState = MasterState::class.java.name
      )
      
      stateMachine.apply {
        networkStateHolder.set(
          MasterState(
            ncStateMachine = this@apply,
            netController = netController,
            clusterNodesHandler = clusterNodesHandler,
            playerInfo = event.gamePlayerInfo,
            gameConfig = event.internalGameConfig,
            state = event.gameState,
          )
        )
      }
    }
    
    private suspend fun switchToLobby(
      stateMachine: NetworkStateMachine,
      event: StateEvent.ControllerEvent.SwitchToLobby,
    ) {
      val curState = stateMachine.networkStateHolder.get()
      if(curState is LobbyState) throw IllegalChangeStateAttempt(
        fromState = curState.javaClass.name,
        toState = LobbyState::class.java.name
      )
      
      stateMachine.masterDeputyHolder.set(null)
      curState.cleanup()
      
      stateMachine.apply {
        networkStateHolder.set(
          LobbyState(
            ncStateMachine = this@apply,
            controller = netController,
            netNodesHandler = netNodesHandler,
          )
        )
      }
    }
  }
}