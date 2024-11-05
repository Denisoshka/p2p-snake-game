package d.zhdanov.ccfit.nsu.core.network

import core.network.nethandlers.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.interaction.v1.bridges.PlayerContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
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

class NodesHolder<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
  contextJoinBacklog: Int,
  val messageUtils: MessageUtilsT<MessageT, MessageType>,
  private val pingDelay: Long,
  private val resendDelay: Long,
  private val thresholdDelay: Long,
  private val unicastNetHandler: UnicastNetHandler<MessageT, MessageType, InboundMessageTranslator>,
  private val multicastNetHandler: MulticastNetHandler<MessageT, MessageType, InboundMessageTranslator>,
  private val messageTranslator: InboundMessageTranslator,
  private val contextNodeFabric: ContextNodeFabricT<MessageT, InboundMessageTranslator>
) : AutoCloseable {
  init {
    unicastNetHandler.configure(this)
    multicastNetHandler.configure(this)
    p2pContextWatcher = getP2PContextWatcher();
  }

  private val joinQueueIsFull =
    "The join queue is full. Please try again later."
  private val joinNotForMaster = "Join request allowed for master node."

  private val commandHandler: CommandHandler<MessageT, InboundMessageTranslator> =
    CommandHandler(this)
  private val messageComparator = messageUtils.getComparator()
  private val contextNodeDispatcher = Dispatchers.IO

  @Volatile
  private var localNode: Node<MessageT, InboundMessageTranslator> = TODO()

  @Volatile
  private var masterNode: Node<MessageT, InboundMessageTranslator> = TODO()

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

  //  в обычном режиме храним только мастера, в режиме сервера храним уже всех
  //  в обычном режиме мы тут храним только мастера и себя, когда заметили
  private val nodes: ConcurrentHashMap<
      InetSocketAddress, Pair<Node<MessageT, InboundMessageTranslator>, PlayerContext>
      > = TODO("implement me plesae")

  override fun close() {
    unicastNetHandler.close()
    multicastNetHandler.close()
    p2pContextWatcher.cancel()
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

      MessageType.DiscoverMsg -> commandHandler.discoverHandle(
        inetSocketAddress, message, msgT
      )

      else -> logger.info {
        "$msgT from $inetSocketAddress not handle as unicast command"
      }
    }
  }

  /**
   * send message without add for acknowledgement monitoring
   * */
  fun retrySendMessage(
    msg: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) = unicastNetHandler.sendMessage(msg, node.address)


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
    val error = messageUtils.getErrorMsg(message, joinQueueIsFull)
    node.addMessageForAcknowledge(message)
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
  suspend fun removeNode(
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

  private suspend fun newNodeRegister(node: Node<MessageT, InboundMessageTranslator>) {
//    nodes.putIfAbsent()
  }

  //  ну вроде функция закончена
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
      val gameMessage = GameMessage(seq, msg)
      //todo сделать receiverID  и в RoleChangeMsg
      //todo что делать если мы так и не доставили RoleChangeMsg? - ну вообще
      // мы же их добавляем в очередь на прием, да и по идее мы же все
      // сообщим из state
      sendMessage(gameMessage, node)
    }
  }

  fun sendMessage(
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
      if (node.nodeState != Node.NodeState.Runnable
        && node.nodeRole == NodeRole.NORMAL
      ) {
        node.nodeRole = NodeRole.DEPUTY
        deputyNode = node
        break
      }
    }
    nodes.entries
    return deputyNode
  }

  private fun getP2PContextWatcher(): Job {
    return CoroutineScope(contextNodeDispatcher).launch {
      //todo можно сделать select который будет принимать сообщение от
      // запустившейся ноды
      while (true) {
        select {
          deadNodeQueue.onReceive { node -> removeNode(node) }
          newNodeRegister.onReceive { node -> newNodeRegister(node) }
        }
      }
    }
  }


  class CommandHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
    private val context: NodesHolder<MessageT, InboundMessageTranslator>
  ) {
    private val translator = context.messageTranslator
    private val nodes = context.nodes

    fun joinHandle(
      address: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      val msg = (translator.fromMessageT(message, msgT)).msg as JoinMsg
      if (!context.checkJoinPreconditions(msg)) return
      val pair = nodes[address]
      if (pair == null) {
        val node = context.contextNodeFabric.create()
        if (!context.joinQueue.offer(node)) {
          context.retryJoinLater(message, node)
        }
      } else {

      }

      TODO("нужно здесь зделать как то по другому")
      TODO("Not yet implemented")
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
      inetSocketAddress: InetSocketAddress, message: MessageT, msgT: MessageType
    ) {
      TODO("Not yet implemented")
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
