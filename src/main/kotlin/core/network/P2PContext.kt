package d.zhdanov.ccfit.nsu.core.network

import core.network.nethandlers.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.interaction.v1.bridges.PlayerContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.network.nethandlers.MulticastNetHandler
import d.zhdanov.ccfit.nsu.core.network.utils.ContextNodeFabricT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageUtilsT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class P2PContext<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
  contextJoinBacklog: Int,
  val messageUtils: MessageUtilsT<MessageT, MessageType>,
  private val pingDelay: Long,
  private val resendDelay: Long,
  private val thresholdDelay: Long,
  private val messageTranslator: InboundMessageTranslator,
  private val contextNodeFabric: ContextNodeFabricT<MessageT, InboundMessageTranslator>
) {
  private val joinQueueIsFull =
    "The join queue is full. Please try again later."
  private val joinNotForMaster = "Join request allowed for master node."

  private val contextNodeDispatcher = Dispatchers.IO
  private val messageComparator = messageUtils.getComparator()
  private val unicastNetHandler: UnicastNetHandler<MessageT, MessageType> =
    TODO()
  private val multicastNetHandler: MulticastNetHandler<MessageT> = TODO()
  private val localNode: Node<MessageT, InboundMessageTranslator>

  @Volatile
  private var masterNode: Node<MessageT, InboundMessageTranslator>

  @Volatile
  private var deputyNode: Node<MessageT, InboundMessageTranslator>?

  @Volatile
  private var nodeState: NodeRole = NodeRole.NORMAL

  private val joinQueue: BlockingQueue<Node<MessageT, InboundMessageTranslator>> =
    ArrayBlockingQueue(contextJoinBacklog)
  private val deadNodeQueue: Channel<Node<MessageT, InboundMessageTranslator>> =
    Channel(contextJoinBacklog)
  val newNodeRegister: Channel<Node<MessageT, InboundMessageTranslator>> =
    Channel(contextJoinBacklog)
  private var p2pContextWatcher: Job

  init {
    p2pContextWatcher = getP2PContextWatcher();
  }


  //  в обычном режиме храним только мастера, в режиме сервера храним уже всех
  //  в обычном режиме мы тут храним только мастера и себя, когда заметили
  private val nodes: ConcurrentHashMap<InetSocketAddress, Pair<Node<MessageT, InboundMessageTranslator>, PlayerContext>> =
    TODO("implement me plesae")

  fun handleUnicastMessage(
    message: MessageT, inetSocketAddress: InetSocketAddress
  ) {
    when (val msgT = messageTranslator.getMessageType(message)) {
      MessageType.PingMsg -> pingHandle(inetSocketAddress, message, msgT)
      MessageType.AckMsg -> ackHandle(inetSocketAddress, message, msgT)
      MessageType.StateMsg -> stateHandle(inetSocketAddress, message, msgT)
      MessageType.JoinMsg -> joinHandle(inetSocketAddress, message, msgT)
      MessageType.SteerMsg -> steerHandle(inetSocketAddress, message, msgT)
      MessageType.ErrorMsg -> errorHandle(inetSocketAddress, message, msgT)
      MessageType.RoleChangeMsg -> roleChangeHandle(
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
      MessageType.AnnouncementMsg -> announcementHandle(
        inetSocketAddress, message, msgT
      )

      MessageType.DiscoverMsg -> discoverHandle(
        inetSocketAddress, message, msgT
      )

      else -> logger.info {
        "$msgT from $inetSocketAddress not handle as unicast command"
      }
    }
  }

  private fun steerHandle(
    address: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  private fun handleMessage(
    message: MessageT, inetSocketAddress: InetSocketAddress
  ) {
    val node = nodes[inetSocketAddress] ?: return
    val msg = messageTranslator.getMessageType(message)
    when (message)

    TODO(
      "в роли обычного просматривающего нужно дождаться первого state " + "и только после этого играть?"
    )
  }

  fun retrySendMessage(
    msg: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) {
    unicastNetHandler.sendMessage(msg, node.address)
  }


  private fun masterHandleMessage(
    message: MessageT, address: InetSocketAddress
  ) {
    val msgT = messageTranslator.getMessageType(message)
    if (msgT == MessageType.JoinMsg) {

      return
    }
    val pair = nodes[address] ?: return
    val node = pair.first
    if (messageUtils.needToAcknowledge(msgT)) {
      acknowledgeMessage(message, node)
    }
    val transaction = messageTranslator.fromMessageT(message, msgT)
    applyMessage(transaction)
  }

  private fun applyMessage(transaction: GameMessage) {
    TODO("Not yet implemented")
  }

  private fun joinNode(
    ipAddress: InetSocketAddress, message: GameMessage
  ) {
    if (message.msg.type != MessageType.JoinMsg) return
    val joinMsg = message.msg as JoinMsg;
    if (!checkJoinPreconditions(joinMsg)) return
    nodes[ipAddress] ?: return

    val newNode = contextNodeFabric.create()
    nodes[ipAddress] = newNode;
    val ret = joinQueue.offer(newNode)
    if (ret) {
      nodeOffered(TODO(), newNode)
    } else {
      retryJoinLater(TODO(), newNode)
    }
    TODO("нужно здесь зделать как то по другому")
  }

  private fun checkJoinPreconditions(joinMsg: JoinMsg): Boolean {
    return joinMsg.nodeRole == NodeRole.NORMAL
        || joinMsg.nodeRole == NodeRole.VIEWER
  }

  private fun nodeOffered(
    message: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) {
    TODO("нужно дождаться ответа от движка и все?")
  }

  private fun retryJoinLater(
    message: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) {
    val error = messageUtils.getErrorMsg(message, joinQueueIsFull)
    unicastNetHandler.sendMessage(error, node.address)
  }

  private fun acknowledgeMessage(
    message: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) {
    val ackMsg = messageUtils.getAckMsg(message, localNode.nodeId, node.nodeId)
    unicastNetHandler.sendMessage(ackMsg, node.address)
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
  suspend fun nodeNotResponding(node: Node<MessageT, InboundMessageTranslator>) {/*
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
    if (localNode == node) vsePoshloPoPizde()
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

  private fun vsePoshloPoPizde() {

  }

  private fun newNodeRegister(node: Node<MessageT, InboundMessageTranslator>) {

  }

  //  ну вроде функция закончена
  private suspend fun deputyOnMasterDead() {/*todo a y нас здесь не может быть конкурентной ситуации когда добавилась
        вот только только добавилась нода? а вот здесь мы не нашлик андидата на
        deputy, вообще можно просто при коннекте ноды чекать стейт кластера и
        смотреть есть ли депутя
        */
    deputyNode = chooseAndSetNewDeputy()
    masterNode = localNode
    localNode.nodeRole = NodeRole.MASTER
    val messages = masterNode.getUnacknowledgedMessages()
    for (msg in messages) {
      val gMsg = messageTranslator.fromMessageT(msg)
      applyMessage(gMsg)
    }
    nodeState = NodeRole.MASTER
    /**
     * selects a new [NodeRole.DEPUTY] and informs all players about this
     * change using a
     * [d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg].
     */
//    todo что то я переживаю о ноде которая может присоединиться во время сего
//    действия, хотя ведь она присоединяется в этом же контексте так что
//    должна узнать по свойсту линейным порядка исполнения кто депутя
    for ((_, pair) in nodes) {
      val node = pair.first
      val cond = deputyNode?.let { it.address == node.address } == true
      val msg = if (cond) {
        RoleChangeMsg(NodeRole.MASTER)
      } else {
        RoleChangeMsg(NodeRole.MASTER, NodeRole.DEPUTY)
      }
      val seq = node.getNextMSGSeqNum();
      val gameMessage = GameMessage(seq, msg)
      //todo сделать receiverID  и в RoleChangeMsg
      //todo что делать если мы так и не доставили RoleChangeMsg? - ну вообще
      // мы же их добавляем в очередь на прием, да и по идее мы же все
      // сообщим из state
      sendMessage(gameMessage, node)
    }
  }

  private fun sendMessage(
    msg: GameMessage, node: Node<MessageT, InboundMessageTranslator>
  ) {
  }

  private suspend fun normalOnMasterDead() {
    nodes.remove(masterNode.address)
    /**
     * Processes the current state received from the cluster.
     *
     * If we are aware of other nodes in the cluster but do not receive notification
     * of a new leader election, we assume that we have been disconnected from the cluster.
     *
     * However, if we appear to be the only remaining node, we designate ourselves as the master.
     */

    //  сука мы храним же ноды в чем хороший вопрос
    if (deputyNode == null) {
      nodeState = NodeRole.MASTER
    }
    val newDeputy = chooseAndSetNewDeputy() ?: return
    val unacknowledgedMessages = masterNode.getUnacknowledgedMessages()
    for (message in unacknowledgedMessages) {
      newDeputy.addMessageForAcknowledge(message)
      unicastNetHandler.sendMessage(message, newDeputy.address)
    }
  }

  //  todo как обрабатывать ситуацию когда у нас умерли все ноды и нет больше кандидатов
  private suspend fun masterOnDeputyDead() {
    val newDeputy = chooseAndSetNewDeputy() ?: return
    val msg = GameMessage(RoleChangeMsg()) //todo fix this
    sendMessage(msg, newDeputy)
  }

  /**
   * Selects a new deputy, assigns it to [deputyNode], and returns it. If no
   * suitable candidate for deputy is found, assigns and returns `null`.
   *
   * @return [deputyNode] if a deputy was chosen, otherwise `null`.
   */
  private fun chooseAndSetNewDeputy(): Node<MessageT, InboundMessageTranslator>? {
    deputyNode?.let { nodes.remove(it.address) }
    deputyNode = null
    for ((_, pair) in nodes) {
      val node = pair.first
      if (node.nodeCondition != NodeState.Terminated && node.nodeRole == NodeRole.NORMAL) {
        node.nodeRole = NodeRole.DEPUTY
        deputyNode = node
        break
      }
    }
    return deputyNode
  }

  private fun getP2PContextWatcher(): Job {
    return CoroutineScope(contextNodeDispatcher).launch {
      //todo можно сделать select который будет принимать сообщение от
      // запустившейся ноды
      while (true) {
        select {
          deadNodeQueue.onReceive { node -> nodeNotResponding(node) }
          newNodeRegister.onReceive { node -> newNodeRegister(node) }
        }
      }
    }
  }

  private fun joinHandle(
    address: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    val msg = (messageTranslator.fromMessageT(message, msgT)).msg as JoinMsg
    joinNode(address, msg)
    TODO("Not yet implemented")
  }

  private fun pingHandle(
    inetSocketAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    val nodeAndPlayerContext = nodes[inetSocketAddress] ?: return
    val node = nodeAndPlayerContext.first
    node.addMessageForAcknowledge(message)
    acknowledgeMessage(message, node)
  }

  private fun ackHandle(
    inetSocketAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    val nodeAndPlayerContext = nodes[inetSocketAddress] ?: return
    val node = nodeAndPlayerContext.first
    node.approveMessage(message)
  }

  private fun stateHandle(
    address: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {

  }

  private fun discoverHandle(
    address: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  private fun roleChangeHandle(
    inetSocketAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  private fun announcementHandle(
    address: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  private fun errorHandle(
    address: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }
}
