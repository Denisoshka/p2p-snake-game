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
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.NetworkStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.events.StateEvents
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.ActiveState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.LobbyState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.MasterState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.PassiveState
import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeT
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.network.core.states.node.lobby.impl.NetNodeHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkStateContext
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkStateObserver
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
) : NetworkStateContext, NetworkStateObserver, StateConsumer {
  private val seqNumProvider = AtomicLong(0)
  private val stateNumProvider = AtomicInteger(0)
  private val clusterNodesHandler: ClusterNodesHandler = TODO()
  private val netNodesHandler: NetNodeHandler = TODO()
  private val contextScope: CoroutineScope
  private val deadNodeChannel = Channel<ClusterNode>(TODO())
  private val registerNewNode = Channel<ClusterNode>(TODO())
  private val detachNodeChannel = Channel<ClusterNode>(TODO())

  val nextSegNum
    get() = seqNumProvider.incrementAndGet()

  @Volatile var nodeId = 0
    private set

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

    val p2pmsg = GameMessage(nextSegNum, state)
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
  private fun chooseSetNewDeputy(oldDeputyId: Int): NodeT? {
    val (masterInfo, _) = masterDeputyHolder.get()
                          ?: throw IllegalChangeStateAttempt(
                            "current master deputy missing "
                          )

    val deputyCandidate = clusterNodesHandler.find {
      it.value.nodeState == NodeT.NodeState.Active && it.value.payload != null && it.value.nodeId != oldDeputyId
    }?.value

    val newDeputyInfo = deputyCandidate?.let {
      Pair(it.ipAddress, it.nodeId)
    }

    masterDeputyHolder.set(Pair(masterInfo, newDeputyInfo))

    return deputyCandidate
  }

  override suspend fun handleNodeDetach(node: NodeT) {
    try {
      when(val st = networkState) {
        is MasterState  -> masterHandleNodeDetach(st, node)
        is ActiveState  -> activeHandleNodeDetach(st, node)
        is PassiveState -> passiveHandleNodeDetach(st, node)
      }
    } catch(e: Exception) {
      Logger.error(e) { "during node detach" }
      if(e !is IllegalNodeRegisterAttempt && e !is IllegalChangeStateAttempt) throw e
    }
  }

  /**
   * Мы меняем состояние кластера в одной функции так что исполнение линейно
   */
  private fun CoroutineScope.nodesWatcherRoutine() = launch {
    try {
      while(isActive) {
        try {
          select {
            detachNodeChannel.onReceive { node ->

              handleNodeDetach(node)

            }
            deadNodeChannel.onReceive { node ->
              handleNodeDetach(node)
            }
            registerNewNode.onReceive { node ->
              handleNodeRegister(node)
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
    }
    Logger.info { "nodesWatcherRoutine finished" }
  }

  /**
   * @throws IllegalChangeStateAttempt
   * */
  private suspend fun passiveHandleNodeDetach(st: PassiveState, node: NodeT) {
    val (msInfo, depInfo) = masterDeputyHolder.get() ?: return
    if(msInfo.second != node.nodeId) throw IllegalChangeStateAttempt(
      "non master node $node in passiveHandleNodeDetach"
    )
    if(depInfo != null) {
      changeNormalChangeInfoDeputyToMaster(depInfo, node)
    } else {
      handleSwitchToLobby(StateEvents.ControllerEvent.SwitchToLobby)
    }
  }

  private suspend fun activeHandleNodeDetach(st: ActiveState, node: NodeT) {
    val (msInfo, depInfo) = masterDeputyHolder.get() ?: return
    if(depInfo == null) {
      Logger.warn { "activeHandleNodeDetach depInfo absent" }

      handleSwitchToLobby(StateEvents.ControllerEvent.SwitchToLobby)
      return
    }

    if(node.nodeId == msInfo.second) {
      if(nodeId == depInfo.second) {
        activeDeputyHandleMasterDetach(node, depInfo)
      } else {
        activeNormalHandleMasterDetach(node, depInfo)
      }
    } else {
      throw IllegalChangeStateAttempt(
        "non master $node detached from cluster in state $networkState"
      )
    }
  }

  private suspend fun masterHandleNodeDetach(st: MasterState, node: NodeT) {
    val (_, depInfo) = masterDeputyHolder.get() ?: return
    if(node.nodeId != depInfo?.second) return
    val newDep = chooseSetNewDeputy(node.nodeId) ?: return

    /**
     * choose new deputy
     */
    val outMsg = MessageUtils.MessageProducer.getRoleChangeMsg(
      msgSeq = nextSegNum,
      senderId = nodeId,
      receiverId = newDep.nodeId,
      senderRole = SnakesProto.NodeRole.MASTER,
      receiverRole = SnakesProto.NodeRole.DEPUTY
    )

    netController.sendUnicast(outMsg, newDep.ipAddress)
  }

  private suspend fun activeDeputyHandleMasterDetach(
    node: NodeT,
    depInfo: Pair<InetSocketAddress, Int>
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
        handleSwitchToLobby(StateEvents.ControllerEvent.SwitchToLobby)
      }

      else -> {
        masterDeputyHolder.set(depInfo to null)
        Logger.info { "activeDeputyHandleMasterDetach MasterNow" }
        handleMasterNowEvent(
          StateEvents.InternalEvent.MasterNow(
            gameState = state.first,
            playerInfo = TODO(),
            gameConfig = TODO(),
          )
        )
      }
    }
  }

  private fun activeNormalHandleMasterDetach(
    node: NodeT, depInfo: Pair<InetSocketAddress, Int>
  ) {
    changeNormalChangeInfoDeputyToMaster(depInfo, node)
  }

  private fun changeNormalChangeInfoDeputyToMaster(
    depInfo: Pair<InetSocketAddress, Int>, node: NodeT
  ) {
    masterDeputyHolder.set(Pair(depInfo, null))
    val unacknowledgedMessages = node.getUnacknowledgedMessages()
    val newMasterClusterNode = ClusterNode(
      nodeState = NodeT.NodeState.Active,
      nodeId = depInfo.second,
      ipAddress = depInfo.first,
      payload = null,
      clusterNodesHandler = clusterNodesHandler
    )
    clusterNodesHandler.registerNode(newMasterClusterNode)
    node.addAllMessageForAck(unacknowledgedMessages)
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
    val node = clusterNodesHandler[ipAddress] ?: return
    val ack = MessageUtils.MessageProducer.getAckMsg(
      message.msgSeq, nodeId, node.nodeId
    )
    netController.sendUnicast(ack, node.ipAddress)
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

  fun changeState(event: StateEvents) {
    try {
      when(event) {
        is StateEvents.ControllerEvent.Join          -> {
          handleJoin(event)
        }

        is StateEvents.ControllerEvent.LaunchGame    -> {
          handleLaunchGame(event)
        }

        is StateEvents.ControllerEvent.SwitchToLobby -> {
          handleSwitchToLobby(event)
        }

        is StateEvents.InternalEvent.JoinAck         -> {
          handleJoinAck(event)
        }

        is StateEvents.InternalEvent.MasterNow       -> {
          handleMasterNowEvent(event)
        }
      }
    } catch(e: Exception) {
      Logger.error(e) { "change state failed" }
    }
  }

  private fun handleJoin(event: StateEvents.ControllerEvent.Join) {
    val state = networkStateHolder.get()
    if(state is LobbyState) {
      state.sendJoinMsg(event)
    }
  }

  @Synchronized
  private fun handleLaunchGame(event: StateEvents.ControllerEvent.LaunchGame) {
    val curState = networkStateHolder.get()
    if(curState !is LobbyState) throw throw IllegalChangeStateAttempt(
      fromState = curState.javaClass.name,
      toState = MasterState::class.java.name
    )

    val plInfo = GamePlayer(
      name = event.playerName,
      id = nodeId,
      nodeRole = NodeRole.MASTER,
      playerType = PlayerType.HUMAN,
      score = 0,
      ipAddress = null,
      port = null,
    )

    networkStateHolder.set(
      MasterState(
        ncStateMachine = this,
        netController = netController,
        clusterNodesHandler = clusterNodesHandler,
        gameConfig = event.gameConfig,
        playerInfo = plInfo,
        state = null
      )
    )
  }

  private suspend fun handleSwitchToLobby(
    event: StateEvents.ControllerEvent.SwitchToLobby
  ) {
    val curState = networkStateHolder.get()
    if(curState is LobbyState) return

    curState.cleanup()

    networkStateHolder.set(
      LobbyState(
        ncStateMachine = this,
        controller = netController,
        netNodesHandler = netNodesHandler,
      )
    )
  }

  private fun handleJoinAck(event: StateEvents.InternalEvent.JoinAck) {
    val curState = networkStateHolder.get()
    if(curState !is LobbyState) {
      throw IllegalChangeStateAttempt(
        fromState = curState.javaClass.name,
        toState = MasterState::class.java.name
      )
    }
    TODO()
  }

  private suspend fun handleMasterNowEvent(
    event: StateEvents.InternalEvent.MasterNow
  ) {
    val curState = networkStateHolder.get()
    if(curState !is ActiveState) throw IllegalChangeStateAttempt(
      fromState = curState.javaClass.name,
      toState = MasterState::class.java.name
    )
    networkStateHolder.set(
      MasterState(
        ncStateMachine = this,
        netController = netController,
        clusterNodesHandler = clusterNodesHandler,
        playerInfo = event.playerInfo,
        gameConfig = event.gameConfig,
        state = event.gameState,
      )
    )
  }
}