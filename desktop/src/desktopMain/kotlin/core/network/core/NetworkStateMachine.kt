package d.zhdanov.ccfit.nsu.core.network.core

import core.network.core.Node
import core.network.core.NodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.controllers.dto.GameAnnouncement
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.AckMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeDestination
import d.zhdanov.ccfit.nsu.core.network.core.states.events.StateEvents
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.ActiveState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.LobbyState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.MasterState
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkStateHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import d.zhdanov.ccfit.nsu.core.network.interfaces.StateConsumer
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
) : NetworkStateHandler, StateConsumer {
  private val seqNumProvider = AtomicLong(0)
  private val stateNumProvider = AtomicInteger(0)
  val nextSegNum
    get() = seqNumProvider.incrementAndGet()
  val nextStateNum
    get() = stateNumProvider.getAndIncrement()
  private val nodesHandler: NodesHandler = TODO()

  @Volatile var nodeId = 0
    private set
  private val state: AtomicReference<NetworkState> = AtomicReference(
    LobbyState(
      ncStateMachine = this,
      controller = netController,
      nodesHandler = nodesHandler,
    )
  )

  val networkState: NetworkState
    get() = state.get()
  val latestGameState = AtomicReference<Pair<StateMsg, Int>?>()
  val masterDeputy: AtomicReference<Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?> =
    AtomicReference()


  override fun submitSteerMsg(
    steerMsg: SteerMsg
  ) = state.get().submitSteerMsg(steerMsg)

  override fun submitState(
    state: StateMsg,
    acceptedPlayers: List<Pair<Pair<Node, String>, ActiveEntity?>>
  ) {
    val st = this.state.get();
    if(st !is MasterState) return
    val msdp = masterDeputy.get() ?: return

    val (ms, dp) = msdp
    st.player.shootContextState(state, ms, dp)

    for((_, node) in nodesHandler) {
      node.payload?.shootContextState(state, ms, dp)
    }

    val stateNum = stateNumProvider.incrementAndGet()
    state.stateOrder = stateNum

    val p2pmsg = P2PMessage(nextSegNum, state)
    val protomsg = MessageTranslator.toMessageT(p2pmsg, MessageType.StateMsg)

    for((ipAddr, node) in nodesHandler) {
      val nodest = node.nodeState
      if(NodeT.isRunning(nodest)) continue

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
  ) = state.get().joinHandle(ipAddress, message, msgT)

  override fun pingHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = state.get().pingHandle(ipAddress, message, msgT)


  override fun ackHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = state.get().ackHandle(ipAddress, message, msgT)


  override fun stateHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = state.get().stateHandle(ipAddress, message, msgT)


  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = state.get().roleChangeHandle(ipAddress, message, msgT)


  override fun announcementHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = state.get().announcementHandle(ipAddress, message, msgT)


  override fun errorHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = state.get().errorHandle(ipAddress, message, msgT)


  override fun steerHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = state.get().steerHandle(ipAddress, message, msgT)


  override fun handleMasterDeath(
    master: Node
  ) = state.get().handleMasterDeath(master)

  override fun handleNodeDetach(
    node: Node
  ) = state.get().handleNodeDetach(node)

  fun getP2PAck(
    message: GameMessage, node: Node
  ): P2PMessage {
    val ack = AckMsg()
    val p2pmsg = P2PMessage(message.msgSeq, ack, nodeId, node.id)
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
  fun chooseSetNewDeputy(): Node? {
    val (masterInfo, _) = masterDeputy.get() ?: return null

    val deputyCandidate = nodesHandler.find {
      it.value.nodeState == NodeT.NodeState.Active
    }?.value

    val newDeputyInfo = deputyCandidate?.let {
      it.nodeRole = NodeRole.DEPUTY
      Pair(it.ipAddress, it.id)
    }

    masterDeputy.set(Pair(masterInfo, newDeputyInfo))
    return deputyCandidate
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
    val node = nodesHandler.getNode(ipAddress) ?: return
    val outp2p = getP2PAck(message, node)
    val outmsg = MessageTranslator.toMessageT(
      outp2p, MessageType.AckMsg
    )
    netController.sendUnicast(outmsg, node.ipAddress)
  }

  /**
   * provide base action on ack msg
   */
  fun onAckMsg(
    ipAddress: InetSocketAddress, message: GameMessage
  ) = nodesHandler.getNode(ipAddress)?.ackMessage(message)


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
        is StateEvents.ControllerEvent.JoinAsNormal  -> TODO()
        is StateEvents.ControllerEvent.JoinAsViewer  -> TODO()
        is StateEvents.ControllerEvent.LaunchGame    -> TODO()
        is StateEvents.ControllerEvent.SwitchToLobby -> handleSwitchToLobby(
          event
        )

        is StateEvents.InternalEvent.JoinAsNormalAck -> TODO()
        is StateEvents.InternalEvent.JoinAsViewerAck -> handleJoinAsViewerAck(
          event
        )

        is StateEvents.InternalEvent.MasterNow       -> handleMasterNowEvent(
          event
        )
      }
    } catch(e: Exception) {
      Logger.error(e) { "change state failed" }
    }
  }

  @Synchronized
  private fun handleSwitchToLobby(event: StateEvents.ControllerEvent.SwitchToLobby) {
    val curState = state.get()
    if(curState is LobbyState) return

    curState.cleanup()

    state.set(
      LobbyState(
        ncStateMachine = this,
        controller = netController,
        nodesHandler = nodesHandler,
      )
    )
  }

  @Synchronized
  private fun handleJoinAsViewerAck(event: StateEvents.InternalEvent.JoinAsViewerAck) {
    val curState = state.get()
    if(curState !is LobbyState) {
      throw IllegalChangeStateAttempt(
        fromState = curState.javaClass.name,
        toState = MasterState::class.java.name
      )
    }
  }

  @Synchronized
  private fun handleMasterNowEvent(event: StateEvents.InternalEvent.MasterNow) {
    val curState = state.get()
    if(curState !is ActiveState) throw IllegalChangeStateAttempt(
      fromState = curState.javaClass.name,
      toState = MasterState::class.java.name
    )

    state.set(
      MasterState(
        ncStateMachine = this,
        netController = netController,
        nodesHandler = nodesHandler,
        playerInfo = event.playerInfo,
        state = event.gameState
      )
    )

  }


  override fun initialize() {
  }

  override fun cleanup() {}

}