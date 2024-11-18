package d.zhdanov.ccfit.nsu.core.network.core

import core.network.core.Node
import core.network.core.NodesHandler
import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.*
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeDestination
import d.zhdanov.ccfit.nsu.core.network.core.states.ActiveState
import d.zhdanov.ccfit.nsu.core.network.core.states.LobbyState
import d.zhdanov.ccfit.nsu.core.network.core.states.MasterState
import d.zhdanov.ccfit.nsu.core.network.core.states.PassiveState
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkStateHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger(NetworkStateMachine::class.java.name)
private val kPortRange = 1..65535


class NetworkStateMachine<MessageT, InboundMessageTranslatorT : MessageTranslatorT<MessageT>, PayloadT : NodePayloadT>(
  private val netController: NetworkController<MessageT, InboundMessageTranslatorT, PayloadT>
) : NetworkStateHandler<MessageT, InboundMessageTranslatorT, PayloadT> {
  val emptyAddress = InetSocketAddress("0.0.0.0", 0)
  val seqNumProvider = AtomicLong(0)
  val utils = netController.messageUtils
  private val nodesHandler: NodesHandler<MessageT, InboundMessageTranslatorT, PayloadT> =
    TODO()
  private val masterState = MasterState(
    this, netController, nodesHandler
  )
  private val activeState = ActiveState(
    this, netController, nodesHandler
  )
  private val passiveState = PassiveState(
    this, netController, nodesHandler
  )
  private val lobbyState = LobbyState(
    this, netController, nodesHandler
  )
  val msgTranslator = netController.messageTranslator

  @Volatile var nodeId = 0
    private set
  private val state: AtomicReference<NetworkState<MessageT, InboundMessageTranslatorT, PayloadT>> =
    AtomicReference(lobbyState)

  val latestGameState = AtomicReference<Pair<StateMsg, Int>?>()
  val masterDeputy: AtomicReference<Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?> =
    AtomicReference()

  val waitToJoin = ConcurrentHashMap<InetSocketAddress, GameConfig>()

  override fun submitSteerMsg(steerMsg: SteerMsg) {
    state.get().submitSteerMsg(steerMsg)
  }

  fun submitJoinMsg(
    joinMsg: JoinMsg,
    address: InetSocketAddress,
    nodeId: Int,
    config: GameConfig
  ) {
    if(state.get() !is LobbyState) return
    val seq = seqNumProvider.getAndIncrement()
    val node = nodesHandler.addNewNode(
      seq,
      NodeRole.MASTER,
      nodeId,
      address,
      false
    )
    val outp2pmsg = P2PMessage(seq, joinMsg, null, nodeId)
    val out = msgTranslator.toMessageT(outp2pmsg, MessageType.JoinMsg)
    node.addMessageForAck(out)
    waitToJoin[address] = config
    sendUnicast(out, address)
  }

  override fun sendUnicast(
    msg: MessageT, nodeAddress: InetSocketAddress
  ) = netController.sendUnicast(msg, nodeAddress)

  override fun joinHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().joinHandle(ipAddress, message, msgT)

  override fun pingHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().pingHandle(ipAddress, message, msgT)


  override fun ackHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().ackHandle(ipAddress, message, msgT)


  override fun stateHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().stateHandle(ipAddress, message, msgT)


  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().roleChangeHandle(ipAddress, message, msgT)


  override fun announcementHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().announcementHandle(ipAddress, message, msgT)


  override fun errorHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().errorHandle(ipAddress, message, msgT)


  override fun steerHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = state.get().steerHandle(ipAddress, message, msgT)


  override fun handleMasterDeath(
    master: Node<MessageT, InboundMessageTranslatorT, PayloadT>
  ) = state.get().handleMasterDeath(master)

  override fun handleNodeDetach(
    node: Node<MessageT, InboundMessageTranslatorT, PayloadT>
  ) = state.get().handleNodeDetach(node)

  fun getP2PAck(
    message: MessageT, node: Node<MessageT, InboundMessageTranslatorT, PayloadT>
  ): P2PMessage {
    val ack = AckMsg()
    val seq = netController.messageUtils.getMSGSeq(message)
    val p2pmsg = P2PMessage(seq, ack, nodeId, node.id)
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
  fun chooseSetNewDeputy(): Node<MessageT, InboundMessageTranslatorT, PayloadT>? {
    fun validDeputy(
      node: Node<MessageT, InboundMessageTranslatorT, PayloadT>
    ): Boolean = node.running && node.nodeState == NodeT.NodeState.Active

    val (masterInfo, _) = masterDeputy.get() ?: return null
    val node = nodesHandler.findNode(::validDeputy)
    val newDeputyInfo = node?.let { Pair(it.ipAddress, it.id) }

    masterDeputy.set(Pair(masterInfo, newDeputyInfo))
    return node
  }

  /**
   * @throws IllegalNodeDestination
   * */
  private fun updateDeputyFromState(state: StateMsg) {
    val depStateInfo = state.players.find { it.nodeRole == NodeRole.DEPUTY }
    val (msInfo, depInfo) = masterDeputy.get() ?: return
    if(depInfo?.second == depStateInfo?.id) return
    val newDepInfo = depStateInfo?.let {
      try {
        Pair(InetSocketAddress(it.ipAddress, it.port!!), it.id)
      } catch(e: Exception) {
        logger.error(e) { "deputy destination has dirty info" }
        throw IllegalNodeDestination(e)
      }
    }/*TODO здесь может быть гонка данных когда мы уже в другом состоянии но
       эта функция долго работает*/
    masterDeputy.set(Pair(msInfo, newDepInfo))
  }

  fun onPingMsg(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    val node = nodesHandler.getNode(ipAddress) ?: return
    val outp2p = getP2PAck(message, node)
    val outmsg = msgTranslator.toMessageT(
      outp2p, MessageType.AckMsg
    )
    netController.sendUnicast(outmsg, node.ipAddress)
  }

  fun onAckMsg(
    ipAddress: InetSocketAddress, message: MessageT
  ) = nodesHandler.getNode(ipAddress)?.ackMessage(message)


  fun onStateMsg(ipAddress: InetSocketAddress, message: MessageT) {
    val (ms, _) = masterDeputy.get() ?: return
    if(ms.first != ipAddress) return

    val p2pMsg = msgTranslator.fromMessageT(message, MessageType.StateMsg)
    val stateMsg = p2pMsg.msg as StateMsg
    val newStateOrder = stateMsg.stateOrder
    while(true) {
      val curState = latestGameState.get() ?: return
      if(newStateOrder <= curState.second) return
      val newState = stateMsg to newStateOrder
      if(!latestGameState.compareAndSet(curState, newState)) return
      else break
    }
    updateDeputyFromState(stateMsg)
  }

  sealed class StateEvents {
    data class StartGame(val role: NodeRole, val gameConfig: GameConfig) :
      StateEvents()
  }

  fun changeState(event: StateEvents) {
    when(val st = state.get()) {
      is LobbyState -> {
        if(event is StateEvents.StartGame) {
          val ret = when(event.role) {
            NodeRole.VIEWER -> state.compareAndSet(st, activeState)
            NodeRole.MASTER -> state.compareAndSet(st, masterState)
            else            -> state.compareAndSet(st, passiveState)
          }
          if(!ret) return
          st = state.get()
        }
      }
    }
  }

  override fun initialize() {
  }

  override fun cleanup() {}
}