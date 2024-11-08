package d.zhdanov.ccfit.nsu.core.network

import core.network.nethandlers.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.network.nethandlers.MulticastNetHandler
import d.zhdanov.ccfit.nsu.core.network.utils.ContextNodeFabricT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageUtilsT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

class P2PContext<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
  contextJoinBacklog: Int,
  val messageUtils: MessageUtilsT<MessageT, MessageType>,
  val pingDelay: Long,
  val resendDelay: Long,
  val thresholdDelay: Long,
  private val unicastNetHandler: UnicastNetHandler<MessageT, MessageType, InboundMessageTranslator>,
  private val multicastNetHandler: MulticastNetHandler<MessageT, MessageType, InboundMessageTranslator>,
  private val messageTranslator: InboundMessageTranslator,
  private val contextNodeFabric: ContextNodeFabricT<MessageT, InboundMessageTranslator>
) : AutoCloseable {
  init {
    unicastNetHandler.configure(this)
    multicastNetHandler.configure(this)
  }

  private val nodesIdSupplier = AtomicLong(0)
  private val joinQueueIsFull =
    "The join queue is full. Please try again later."
  private val joinNotForMaster = "Join request allowed for master node."

  private val commandHandler = CommandHandler(this)
  private val messageComparator = messageUtils.getComparator()


  fun initContext(master: InetSocketAddress?) {

  }

  fun destroyContext(
  ) {
  }

  override fun close() {
    unicastNetHandler.close()
    multicastNetHandler.close()
  }

  fun handleUnicastMessage(
    message: MessageT, inetSocketAddress: InetSocketAddress
  ) {
    when (val msgT = messageTranslator.getMessageType(message)) {
      MessageType.PingMsg -> commandHandler.pingHandle(
        inetSocketAddress, message
      )

      MessageType.AckMsg -> commandHandler.ackHandle(
        inetSocketAddress, message
      )

      MessageType.StateMsg -> commandHandler.stateHandle(
        inetSocketAddress, message, msgT
      )

      MessageType.JoinMsg -> commandHandler.joinHandle(
        inetSocketAddress, message, msgT
      )

      MessageType.SteerMsg -> commandHandler.steerHandle(
        inetSocketAddress, message, msgT
      )

      MessageType.ErrorMsg -> commandHandler.errorHandle(
        inetSocketAddress, message, msgT
      )

      MessageType.RoleChangeMsg -> commandHandler.roleChangeHandle(
        inetSocketAddress, message, msgT
      )

      else -> logger.info {
        "$msgT from $inetSocketAddress not handle as unicast command"
      }
    }
  }

  fun handleMulticastMessage(
    message: MessageT, inetSocketAddress: InetSocketAddress
  ) {
    when (val msgT = messageTranslator.getMessageType(message)) {
      MessageType.AnnouncementMsg -> commandHandler.announcementHandle(
        inetSocketAddress, message, msgT
      )

      else -> logger.debug {
        "$msgT from $inetSocketAddress not handle as unicast command"
      }
    }
  }

  /**
   * send message without add for acknowledgement monitoring
   * */
  fun retrySendMessage(
    msg: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) = unicastNetHandler.sendUnicastMessage(msg, node.address)


  fun clusterTaskStateUpdated(stateMsg: StateMsg) {
    msg = messageTranslator.toMessageT(stateMsg, MessageType.StateMsg)
    for ((_, nodeContext) in nodes) {
      val node = nodeContext.first
      val seg = node.getNextMSGSeqNum()
    }
  }

  private fun checkJoinPreconditions(joinMsg: JoinMsg): Boolean {
    return joinMsg.nodeRole == NodeRole.NORMAL || joinMsg.nodeRole == NodeRole.VIEWER
  }

  private fun retryJoinLater(
    message: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) {

  }

  private fun acknowledgeMessage(
    message: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) {
    val ackMsg = messageUtils.getAckMsg(message, localNode.nodeId, node.nodeId)
    unicastNetHandler.sendUnicastMessage(ackMsg, node.address)
  }


  private fun vsePoshloPoPizde() {

  }


  //  ну вроде функция закончена


  fun sendMessage(
    msg: P2PMessage, node: Node<MessageT, InboundMessageTranslator>
  ) {
  }


  /**
   * Selects a new deputy, assigns it to [deputyNode], and returns it. If no
   * suitable candidate for deputy is found, assigns and returns `null`.
   *
   * @return [deputyNode] if a deputy was chosen, otherwise `null`.
   */
  private fun chooseAndSetNewDeputy(): Node<MessageT, InboundMessageTranslator>? {
    return /*xyi*/
  }

  private fun changeNodeRole(
    node: Node<MessageT, InboundMessageTranslator>, message: P2PMessage
  ) {
    if (message.msg.type != MessageType.RoleChangeMsg) return
    val msg = message.msg as RoleChangeMsg

  }

  private suspend fun handleRegistrationNewNode(node: Node<MessageT, InboundMessageTranslator>) {
//    nodes.putIfAbsent()
  }


  //  todo как обрабатывать ситуацию когда у нас умерли все ноды и нет больше кандидатов
  private suspend fun masterOnDeputyDead() {
    val newDeputy = chooseAndSetNewDeputy() ?: return
    val msg = P2PMessage(RoleChangeMsg()) //todo fix this
    sendMessage(msg, newDeputy)
  }

  suspend fun onContextNodeTerminated(
    node: Node<MessageT, InboundMessageTranslator>
  ) {
    deadNodeChannel.send(node)
  }

  suspend fun onContextNodeConnected(
    node: Node<MessageT, InboundMessageTranslator>
  ) {
    registerNewNode.send(node)
  }

  class ClusterStateHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
    contextJoinBacklog: Int,
    private val context: d.zhdanov.ccfit.nsu.core.network.P2PContext<MessageT, InboundMessageTranslator>
  ) {

    init {
      p2pContextWatcher = getP2PContextWatcher()
    }

    /**
     * Меняем состояние кластера в одном потоке, так что если какая то
     * другая нода подохнет во время смены состояния, то мы это учтем
     */
    /**
     * Changes the state of the cluster in a single thread, so that if any
     * other node fails during the state change, it will be taken into account.
     *
     * This ensures that the system remains consistent and can handle
     * failures gracefully. The following scenarios illustrate how nodes
     * manage role transitions based on their current state:
     * Describes the role management and transitions in a distributed game system.
     *
     * The following scenarios outline how nodes handle the failure
     * of their peers based on their roles:
     *
     * a) **Node [NodeRole.NORMAL] notices that [NodeRole.MASTER] has dropped.**
     * The node replaces the information about the central node with the
     * [NodeRole.DEPUTY]. It starts sending all unicast messages toward the
     * [NodeRole.DEPUTY].
     *
     * b) **Node [NodeRole.MASTER] notices that [NodeRole.DEPUTY] has dropped.**
     * The [NodeRole.MASTER] node selects a new [NodeRole.DEPUTY] from among
     * the [NodeRole.NORMAL] nodes. It informs the current DEPUTY about this
     * change using a
     * [d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg].
     * Other nodes learn about the new [NodeRole.DEPUTY] from a scheduled
     * [d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg],
     * as it is not urgent for them to know immediately.
     *
     * c) **Node with [NodeRole.DEPUTY] role notices that [NodeRole.MASTER] has
     * dropped.** The DEPUTY node becomes the [NodeRole.MASTER] (takes
     * over control of the game). It selects a new [NodeRole.DEPUTY]
     * and informs all players about this change using a
     * [d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg].
     *
     * ***Message regarding role changes in the game.***
     *
     * **This message can take different forms depending on the roles of the
     * participants and their actions:**
     *
     * 1. **From a Deputy** to other players indicating that they should start
     * considering him as the Master. ( *senderRole* = [NodeRole.MASTER])
     *
     * 2. **From an intentionally exiting player**.
     * (*senderRole* = [NodeRole.VIEWER])
     *
     * 3. **From the Master** to a deceased player.
     * (*receiverRole* = [NodeRole.VIEWER])
     *
     * 4. **Appointment of someone as Deputy**.
     * In combination with 1, 2, or separately
     * (*receiverRole* = [NodeRole.DEPUTY])
     *
     * 5. In combination with 2, from the Master to the Deputy indicating
     * that he is becoming the Master.
     * (*receiverRole* = [NodeRole.MASTER])
     */
    private suspend fun handleTerminatedNode(
      node: Node<MessageT, InboundMessageTranslator>
    ) {/*
    * ну вообщем этот контекст будет выполняться в отдельной корутине поэтому
    *  не нужно беспокоиться о линейном порядке исполнения
    * */
      /**
       * [NodeRole] :
       * -> [NodeRole.NORMAL],
       * -> [NodeRole.MASTER],
       * -> [NodeRole.DEPUTY],
       * -> [NodeRole.VIEWER]
       */
      if (nodeState == NodeRole.MASTER) {
        if (node.address == deputyNode?.address) masterOnDeputyDead()
      } else if (nodeState == NodeRole.DEPUTY) {
        if (node.address == masterNode.address) deputyOnMasterDead()
      } else if (nodeState == NodeRole.NORMAL) {
        if (node.address == masterNode.address) normalOnMasterDead()
      } else {
//      todo нужно сделать просто выход из изры ибо мы вивер
      }
    }


    private suspend fun deputyOnMasterDead() {/*
    todo a y нас здесь не может быть конкурентной ситуации когда добавилась
    вот только только добавилась нода? а вот здесь мы не нашли кандидата на
    deputy, вообще можно просто при коннекте ноды чекать стейт кластера и
    смотреть есть ли депутя, изза линейного порядка все будет окей
    */
      deputyNode = chooseAndSetNewDeputy()
      masterNode = localNode
      localNode.nodeRole = NodeRole.MASTER
      nodeState = NodeRole.MASTER
      val messages = masterNode.getUnacknowledgedMessages()
      for (msg in messages) {
        handleUnicastMessage(msg, localNode.address)
      }
      /**
       * selects a new [NodeRole.DEPUTY] and informs all players about this
       * change using a
       * [d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg].
       */
      for ((_, pair) in nodes) {
        val node = pair.first
        val cond = deputyNode?.let { it.address == node.address } == true
        val msg = if (cond) {
          RoleChangeMsg(NodeRole.MASTER)
        } else {
          RoleChangeMsg(NodeRole.MASTER, NodeRole.DEPUTY)
        }
        val seq = node.getNextMSGSeqNum();
        val gameMessage = P2PMessage(seq, msg)
        //todo сделать receiverID  и в RoleChangeMsg
        //todo что делать если мы так и не доставили RoleChangeMsg? - ну вообще
        // мы же их добавляем в очередь на прием, да и по идее мы же все
        // сообщим из state
        sendMessage(gameMessage, node)
      }
    }

    private suspend fun normalOnMasterDead() {
      context.nodes.remove(masterNode.address)
      /**
       * Processes the current state received from the cluster.
       *
       * If we are aware of other nodes in the cluster but do not receive notification
       * of a new leader election, we assume that we have been disconnected from the cluster.
       *
       * However, if we appear to be the only remaining node, we designate ourselves as the master.
       */

      val newDeputy = chooseAndSetNewDeputy() ?: return
      val unacknowledgedMessages = masterNode.getUnacknowledgedMessages()
      for (message in unacknowledgedMessages) {
        newDeputy.addMessageForAcknowledge(message)
        unicastNetHandler.sendUnicastMessage(message, newDeputy.address)
      }
    }
  }

  class CommandHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
    private val context: P2PContext<MessageT, InboundMessageTranslator>
  ) {
    private val translator = context.messageTranslator
    private val nodes = context.nodesByIp

    fun joinHandle(
      ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      context.run {
        if (messageUtils.checkJoinPreconditions(message)) return
        val pair = nodes[ipAddress]
        if (pair == null) {
          val node = contextNodeFabric.create()

        } else {
          val errMsg = messageUtils.newErrorMsg(message, "node already joined")
          unicastNetHandler.sendUnicastMessage(errMsg, ipAddress)
        }/*if (pair == null) {
          val node = contextNodeFabric.create()
          //если нода не предложена, то нужно просто дождаться получатель
          // получит сообщение и бай бай
          if (!joinQueue.offer(node)) {
            val error = messageUtils.getErrorMsg(message, joinQueueIsFull)
            node.apply { setInitialMsg(error); shutdown() }
            unicastNetHandler.sendMessage(error, node.address)
          }
          if (nodes.putIfAbsent(ipAddress, Pair(node, null)) != null) {
            node.shutdownNow()
          }
        } else if (pair.second != null) {
          return
//          просто вернуть Ack
        }*/
//        в ином случае ждем регистрации
      }
    }

    fun pingHandle(
      inetSocketAddress: InetSocketAddress, message: MessageT
    ) {
      val nodeAndPlayerContext = nodes[inetSocketAddress] ?: return
      val node = nodeAndPlayerContext.first
      node.addMessageForAcknowledge(message)
      context.acknowledgeMessage(message, node)
    }

    fun ackHandle(
      inetSocketAddress: InetSocketAddress, message: MessageT
    ) {
      val nodeAndPlayerContext = nodes[inetSocketAddress] ?: return
      val node = nodeAndPlayerContext.first
      node.approveMessage(message)
    }

    fun stateHandle(
      address: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {

    }

    fun discoverHandle(
      address: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    fun roleChangeHandle(
      address: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      context.run {
        val msg = messageTranslator.fromMessageT(message, msgT)
        msg.senderId ?: return
        msg.receiverId ?: return
        val (node, context) = nodes[address] ?: return
        if (context.playerId != msg.senderId ||)
      }
    }

    fun announcementHandle(
      address: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    fun errorHandle(
      address: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
    }

    fun steerHandle(
      address: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      if (context.nodeState != NodeRole.MASTER) {
        return;
      }
      TODO("Not yet implemented")
    }
  }
}
