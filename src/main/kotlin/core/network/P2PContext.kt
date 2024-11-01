package d.zhdanov.ccfit.nsu.core.network

import core.network.nethandlers.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.interaction.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.interaction.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.network.nethandlers.MulticastNetHandler
import d.zhdanov.ccfit.nsu.core.network.utils.ContextNodeFabricT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageUtilsT
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
  private val nodes: ConcurrentHashMap<InetSocketAddress, Node<MessageT, InboundMessageTranslator>> =

  fun handleUnicastMessage(
    message: MessageT, inetSocketAddress: InetSocketAddress
  ) {
    val msgT = messageTranslator.getMessageType(message)
    if (msgT == MessageType.UnrecognisedMsg) {
      return
    } else if (msgT == MessageType.AckMsg) {
      nodes[inetSocketAddress]?.approveMessage(message)
      return
    }

    when (nodeState) {
      NodeRole.MASTER -> masterHandleMessage(message, inetSocketAddress);
      NodeRole.DEPUTY -> TODO()
      else -> handleMessage(message, inetSocketAddress)
    }
    TODO(
      "в роли обычного просматривающего нужно дождаться первого state " + "и только после этого играть?"
    )
  }


  private fun handleMessage(
    message: MessageT, inetSocketAddress: InetSocketAddress
  ) {

  }

  fun sendMessage(
    message: GameMessage, node: Node<MessageT, InboundMessageTranslator>
  ) {
    val msgT = message.msg.type
    val msg = messageTranslator.toMessageT(message, msgT)

    sendMessage(msg, node)
    TODO(
      "с этой функцией нужно что то делать " +
          "ибо здесь твоиться какое то не потребсво"
    )
  }

  fun sendMessage(
    message: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) {

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
      val msg = (messageTranslator.fromMessageT(message, msgT)) as JoinMsg
      joinNode(address, msg)
      return
    }
    val node = nodes[address] ?: return

    if (messageUtils.needToAcknowledge(msgT)) {
      acknowledgeMessage(message, node)
    }

    val innerMsg = messageTranslator.fromMessageT(message, msgT)
    applyMessage(innerMsg)
  }

  private fun joinNode(ipAddress: InetSocketAddress, message: JoinMsg) {
    if (message.nodeRole != NodeRole.NORMAL && message.nodeRole != NodeRole.VIEWER) return
    nodes[ipAddress]?.let { return }

    val newNode = contextNodeFabric.create(
      ipAddress,
      message.,
      message.nodeRole,
      pingDelay,
      resendDelay,
      thresholdDelay,
      this@P2PContext,
      messageComparator,
      contextNodeDispatcher
    )
    nodes[ipAddress] = newNode;
    val ret = joinQueue.offer(newNode)
    if (ret) {
      nodeOffered(TODO(), newNode)
    } else {
      retryJoinLater(TODO(), newNode)
    }
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

  private fun applyMessage(message: GameMessage) {
    TODO("Not yet implemented")
  }

  private fun acknowledgeMessage(
    message: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) {
    val ackMsg = messageUtils.getAckMsg(message, localNode.nodeId, node.nodeId);
    unicastNetHandler.sendMessage(ackMsg, node.address)
  }

  private fun sendMulticast() {}

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
   * [d.zhdanov.ccfit.nsu.core.interaction.messages.types.RoleChangeMsg].
   * Other nodes learn about the new [NodeRole.DEPUTY] from a scheduled
   * [d.zhdanov.ccfit.nsu.core.interaction.messages.types.StateMsg],
   * as it is not urgent for them to know immediately.
   *
   * c) **Node with [NodeRole.DEPUTY] role notices that [NodeRole.MASTER] has
   * dropped.** The DEPUTY node becomes the [NodeRole.MASTER] (takes
   * over control of the game). It selects a new [NodeRole.DEPUTY]
   * and informs all players about this change using a
   * [d.zhdanov.ccfit.nsu.core.interaction.messages.types.RoleChangeMsg].
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
  suspend fun onNodeDead(node: Node<MessageT, InboundMessageTranslator>) {
    /*
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

  private fun newNodeRegister(node: Node<MessageT, InboundMessageTranslator>) {

  }

  //  ну вроде функция закончена
  private suspend fun deputyOnMasterDead() {
    /*todo a y нас здесь не может быть конкурентной ситуации когда добавилась
        вот только только добавилась нода? а вот здесь мы не нашликандидата на
        deputy, вообще можно просто при коннекте ноды чекать стейт кластера и
        смотреть есть ли депутя
        */
    deputyNode = chooseAndSetNewDeputy()
    masterNode = localNode
    val messages = masterNode.getUnacknowledgedMessages()
    for (msg in messages) {
      val gMsg = messageTranslator.fromMessageT(msg)
      applyMessage(gMsg)
    }
    nodeState = NodeRole.MASTER
    /**
     * selects a new [NodeRole.DEPUTY] and informs all players about this
     * change using a
     * [d.zhdanov.ccfit.nsu.core.interaction.messages.types.RoleChangeMsg].
     */
//    что то я переживаю о ноде которая может присоединиться во время сего
//    действия, хотя ведь она присоединяется в этом же контексте так что
//    должна узнать по свойсту линейным порядка исполнения кто депутя
    for ((_, node) in nodes) {
      val msg = if (deputyNode?.let { it.address == node.address } == true) {
        RoleChangeMsg(NodeRole.MASTER)
      } else {
        RoleChangeMsg(NodeRole.MASTER, NodeRole.DEPUTY)
      }
      val seq = node.getNextMSGSeqNum();
      val gameMessage = GameMessage(seq, msg)
      //todo сделать receiverID  и в RoleChangeMsg
      //todo что делать если мы так и не доставили RoleChangeMsg?
      sendMessage(gameMessage, node)
    }
  }

  private fun normalOnMasterDead() {
//    мы же не можем вот это использовать в suspend функциях и + как
    nodes.remove(masterNode.address)
    /**
     * Processes the current state received from the cluster.
     *
     * If we are aware of other nodes in the cluster but do not receive notification
     * of a new leader election, we assume that we have been disconnected from the cluster.
     *
     * However, if we appear to be the only remaining node, we designate ourselves as the master.
     *//*
    * сука мы храним же ноды в
    * */
    if (deputyNode == null) {
      nodeState = NodeRole.MASTER
    }
    val ret = chooseAndSetNewDeputy()
    val unacknowledgedMessages = masterNode.getUnacknowledgedMessages()
    for (message in unacknowledgedMessages) {
      val address = deputyNode.address
      deputyNode.addMessageForAcknowledge(message)
      unicastNetHandler.sendMessage(message, address)
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
    for ((_, node) in nodes) {
      if (node.nodeState != Node.NodeState.Terminated
        && node.address != masterNode.address
      ) {
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
          deadNodeQueue.onReceive { node -> onNodeDead(node) }
          newNodeRegister.onReceive { node -> newNodeRegister(node) }
        }
      }
    }
  }
}