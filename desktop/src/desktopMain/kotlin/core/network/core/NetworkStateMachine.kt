package d.zhdanov.ccfit.nsu.core.network.core

import core.network.core.Node
import core.network.core.NodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.AckMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeDestination
import d.zhdanov.ccfit.nsu.core.network.core.states.*
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkStateHandler
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val Logger = KotlinLogging.logger(NetworkStateMachine::class.java.name)
private val kPortRange = 1..65535

class NetworkStateMachine(
  private val netController: NetworkController,
  private val gameController: GameController
) : NetworkStateHandler {
  private val seqNumProvider = AtomicLong(0)
  val nextSegNum
    get() = seqNumProvider.incrementAndGet()
  private val nodesHandler: NodesHandler =
    TODO()
  private val masterState = MasterState(this, netController, nodesHandler)
  private val activeState = ActiveState(this, netController, nodesHandler)
  private val passiveState = PassiveState(this, netController, nodesHandler)
  private val lobbyState = LobbyState(this, netController, nodesHandler)

  @Volatile var nodeId = 0
    private set
  private val state: AtomicReference<NetworkState> =
    AtomicReference(lobbyState)
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
    acceptedPlayers: Pair<Node, String>
  ) {
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
    fun validDeputy(
      node: Node
    ): Boolean = node.running

    val (masterInfo, _) = masterDeputy.get() ?: return null
    val node = nodesHandler.findNode(::validDeputy)
    val newDeputyInfo = node?.let { Pair(it.ipAddress, it.id) }

    masterDeputy.set(Pair(masterInfo, newDeputyInfo))
    return node
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
      if(!latestGameState.compareAndSet(curState, newState)) return
      else break
    }
    parseState(stateMsg)
    TODO("проверить на корректность")
  }

  fun changeState(event: NetworkStateChangeEvents) {
    synchronized(this) {
      val curState = state.get()
      when(event) {
        is NetworkStateChangeEvents.LaunchGame -> {
          if(curState !is LobbyState) return
          curState.cleanup()
          masterState.config = event.config

          state.set(masterState)
          masterState.initialize()
        }

        is NetworkStateChangeEvents.MasterNow -> {
          if(curState !is ActiveState) return

          state.set(masterState)
          masterState.initialize()
        }

        NetworkStateChangeEvents.SwitchToLobby -> {
          if(curState is LobbyState) return
          curState.cleanup()

          state.set(lobbyState)
          lobbyState.initialize()
        }

        is NetworkStateChangeEvents.JoinAsNormal -> {
          if(curState is LobbyState) return
          curState.cleanup()

          state.set(activeState)
          activeState.initialize()
        }

        is NetworkStateChangeEvents.JoinAsViewer -> {
          if(curState is LobbyState) return

          state.set(passiveState)
          activeState.initialize()
        }
      }
    }
  }

  override fun initialize() {
  }

  override fun cleanup() {}
}