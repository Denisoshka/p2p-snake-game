package d.zhdanov.ccfit.nsu.core.network.core

import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.controllers.dto.GameAnnouncement
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.*
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.AckMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeDestination
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.events.StateEvents
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.ActiveState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.LobbyState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.MasterState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.PassiveState
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl.GameNode
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl.GameNodesHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.core.NetworkState
import d.zhdanov.ccfit.nsu.core.network.interfaces.core.NetworkStateHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.core.NetworkStateObserver
import d.zhdanov.ccfit.nsu.core.network.interfaces.core.StateConsumer
import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeT
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val Logger = KotlinLogging.logger(NetworkStateMachine::class.java.name)
private val kPortRange = 1..65535

class NetworkStateMachine(
  private val netController: NetworkController,
  private val gameController: GameController
) : NetworkStateHandler, NetworkStateObserver, StateConsumer {
  private val seqNumProvider = AtomicLong(0)
  private val stateNumProvider = AtomicInteger(0)
  val nextSegNum
    get() = seqNumProvider.incrementAndGet()
  private val gameNodesHandler: GameNodesHandler = TODO()

  @Volatile var nodeId = 0
    private set
  private val networkStateHolder: AtomicReference<NetworkState> =
    AtomicReference(
      LobbyState(
        ncStateMachine = this,
        controller = netController,
        gameNodesHandler = gameNodesHandler,
      )
    )

  val networkState: NetworkState
    get() = networkStateHolder.get()
  private val latestGameState = AtomicReference<Pair<StateMsg, Int>?>()
  val masterDeputy: AtomicReference<Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?> =
    AtomicReference()


  override fun submitSteerMsg(
    steerMsg: SteerMsg
  ) = networkStateHolder.get().submitSteerMsg(steerMsg)

  override fun submitState(
    state: StateMsg,
    acceptedPlayers: List<Pair<Pair<GameNode, String>, ActiveEntity?>>
  ) {
    val st = this.networkStateHolder.get();
    if(st !is MasterState) return
    val msdp = masterDeputy.get() ?: return

    val (ms, dp) = msdp
    st.player.shootContextState(state, ms, dp)

    for((_, node) in gameNodesHandler) {
      node.payload?.shootContextState(state, ms, dp)
    }

    val stateNum = stateNumProvider.incrementAndGet()
    state.stateOrder = stateNum

    val p2pmsg = P2PMessage(nextSegNum, state)
    val protomsg = MessageTranslator.toMessageT(p2pmsg, MessageType.StateMsg)

    for((ipAddr, node) in gameNodesHandler) {
      if(!node.running) continue

      node.addMessageForAck(protomsg)
      sendUnicast(protomsg, ipAddr)
    }
  }

  override fun joinToGame(announcement: GameAnnouncement) {
    TODO("Not yet implemented")
  }

  override fun sendUnicast(
    msg: GameMessage, nodeAddress: InetSocketAddress
  ) = netController.sendUnicast(msg, nodeAddress)

  override fun joinHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkStateHolder.get().joinHandle(ipAddress, message, msgT)

  override fun pingHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkStateHolder.get().pingHandle(ipAddress, message, msgT)


  override fun ackHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkStateHolder.get().ackHandle(ipAddress, message, msgT)


  override fun stateHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkStateHolder.get().stateHandle(ipAddress, message, msgT)


  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkStateHolder.get().roleChangeHandle(ipAddress, message, msgT)


  override fun announcementHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkStateHolder.get().announcementHandle(ipAddress, message, msgT)


  override fun errorHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkStateHolder.get().errorHandle(ipAddress, message, msgT)


  override fun steerHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkStateHolder.get().steerHandle(ipAddress, message, msgT)

  override fun cleanup() {
    TODO("Not yet implemented")
  }

  fun getP2PAck(
    message: GameMessage, gameNode: GameNode
  ): P2PMessage {
    val ack = AckMsg()
    val p2pmsg = P2PMessage(message.msgSeq, ack, nodeId, gameNode.nodeId)
    return p2pmsg
  }

  fun getP2PRoleChange(
    senderRole: NodeRole?,
    receiverRole: NodeRole?,
    senderId: Int,
    receiverId: Int,
    seq: Long
  ): P2PMessage {
    val roleChange = RoleChangeMsg(senderRole, receiverRole)
    val p2pMsg = P2PMessage(seq, roleChange, senderId, receiverId)
    return p2pMsg
  }

  /**
   * @return `Node<MessageT, InboundMessageTranslatorT, PayloadT>` if new
   * deputy was chosen successfully, else `null`
   */
  private fun chooseSetNewDeputy(oldDeputyId: Int): NodeT? {
    val (masterInfo, _) = masterDeputy.get() ?: return null

    val deputyCandidate = gameNodesHandler.find {
      it.value.nodeState == NodeT.NodeState.Active && it.value.nodeId != oldDeputyId
    }?.value

    val newDeputyInfo = deputyCandidate?.let {
      Pair(it.ipAddress, it.nodeId)
    }

    masterDeputy.set(Pair(masterInfo, newDeputyInfo))

    return deputyCandidate
  }

  override fun handleNodeDetach(node: NodeT) {
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
   * @throws IllegalChangeStateAttempt
   * */
  private fun passiveHandleNodeDetach(st: PassiveState, node: NodeT) {
    val (msInfo, depInfo) = masterDeputy.get() ?: return
    if(msInfo.second != node.nodeId) throw IllegalChangeStateAttempt(
      "non master node $node in passiveHandleNodeDetach"
    )
    if(depInfo != null) {
      changeDeputyToMaster(depInfo, node)
    } else {
      gameController.openLobby()
    }
  }

  private fun activeHandleNodeDetach(st: ActiveState, node: NodeT) {
    val (msInfo, depInfo) = masterDeputy.get() ?: return
    if(depInfo == null) TODO(
      "ну вообще мы не можем сюда зайти в active режиме Deputy"
    )
    if(node.nodeId == msInfo.second) {
      if(nodeId == depInfo.second) {
        activeDeputyHandleMasterDetach(node)
      } else {
        activeNormalHandleMasterDetach(node, depInfo)
      }
    } else {
      Logger.warn { "non master $node detached from cluster in state $networkState" }
    }
  }

  private fun masterHandleNodeDetach(st: MasterState, node: NodeT) {
    val (_, depInfo) = masterDeputy.get() ?: return
    if(node.nodeId != depInfo?.second) return

    val newDep = chooseSetNewDeputy(node.nodeId) ?: return

    val outP2PRoleChange = getP2PRoleChange(
      NodeRole.MASTER, NodeRole.DEPUTY, nodeId, newDep.nodeId, nextSegNum
    )

    val outMsg = MessageTranslator.toMessageT(
      outP2PRoleChange, MessageType.RoleChangeMsg
    )

    netController.sendUnicast(outMsg, newDep.ipAddress)
  }

  private fun activeDeputyHandleMasterDetach(node: NodeT) {
    try {

    } catch(e: IllegalNodeRegisterAttempt) {
      Logger.warn(e) { }
    }
  }

  private fun activeNormalHandleMasterDetach(
    node: NodeT, depInfo: Pair<InetSocketAddress, Int>
  ) {
    try {
      changeDeputyToMaster(depInfo, node)
//      todo можно сделать так чтобы мы начали накапливать сообщения от Deputy
    } catch(e: IllegalNodeRegisterAttempt) {
      Logger.error(e) { "node ${node.nodeId} registered yet" }
    }

  }

  private fun changeDeputyToMaster(
    depInfo: Pair<InetSocketAddress, Int>, node: NodeT
  ) {
    masterDeputy.set(Pair(depInfo, null))
    val unacknowledgedMessages = node.getUnacknowledgedMessages()
    val newMasterGameNode = GameNode(
      messageComparator =,
      nodeState = NodeT.NodeState.Active,
      nodeId = depInfo.second,
      ipAddress = depInfo.first,
      payload = null,
      gameNodesHandler = gameNodesHandler
    )
    gameNodesHandler.registerNode(newMasterGameNode, true)
    node.addAllMessageForAck(unacknowledgedMessages)
  }

  /**
   * @throws IllegalNodeDestination
   * */
  private fun parseState(state: StateMsg) {
    val depStateInfo = state.players.find { it.nodeRole == NodeRole.DEPUTY }
    val (msInfo, depInfo) = masterDeputy.get() ?: return
    if(depInfo?.second == depStateInfo?.id) return
    val newDepInfo = depStateInfo?.let {
      try {
        Pair(InetSocketAddress(it.ipAddress, it.port!!), it.id)
      } catch(e: Exception) {
        Logger.error(e) { "deputy destination has dirty info" }
        throw IllegalNodeDestination(e)
      }
    }/*TODO здесь может быть гонка данных когда мы уже в другом состоянии но
       эта функция долго работает*/
    masterDeputy.set(Pair(msInfo, newDepInfo))
  }

  fun onPingMsg(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
    val node = gameNodesHandler[ipAddress] ?: return
    val outp2p = getP2PAck(message, node)
    val outmsg = MessageTranslator.toMessageT(
      outp2p, MessageType.AckMsg
    )
    netController.sendUnicast(outmsg, node.ipAddress)
  }

  fun onStateMsg(ipAddress: InetSocketAddress, message: GameMessage) {
    val (ms, _) = masterDeputy.get() ?: return
    if(ms.first != ipAddress) return

    val p2pMsg = MessageTranslator.fromProto(message, MessageType.StateMsg)
    val stateMsg = p2pMsg.msg as StateMsg
    val newStateOrder = stateMsg.stateOrder
    while(true) {
      val curState = latestGameState.get() ?: return
      if(newStateOrder <= curState.second) return
      val newState = stateMsg to newStateOrder
      if(latestGameState.compareAndSet(curState, newState)) break
    }
    parseState(stateMsg)
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
    if(state !is LobbyState) return
    val node = gameNodesHandler[event.ipAddress]
    if (node!= null){

    }else{

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
        gameNodesHandler = gameNodesHandler,
        gameConfig = event.gameConfig,
        playerInfo = plInfo,
        state = null
      )
    )
  }

  @Synchronized
  private fun handleSwitchToLobby(event: StateEvents.ControllerEvent.SwitchToLobby) {
    val curState = networkStateHolder.get()
    if(curState is LobbyState) return

    curState.cleanup()

    networkStateHolder.set(
      LobbyState(
        ncStateMachine = this,
        controller = netController,
        gameNodesHandler = gameNodesHandler,
      )
    )
  }

  @Synchronized
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

  @Synchronized
  private fun handleMasterNowEvent(event: StateEvents.InternalEvent.MasterNow) {
    val curState = networkStateHolder.get()
    if(curState !is ActiveState) throw IllegalChangeStateAttempt(
      fromState = curState.javaClass.name,
      toState = MasterState::class.java.name
    )
    networkStateHolder.set(
      MasterState(
        ncStateMachine = this,
        netController = netController,
        gameNodesHandler = gameNodesHandler,
        playerInfo = event.playerInfo,
        gameConfig = event.gameConfig,
        state = event.gameState,
      )
    )
  }
}