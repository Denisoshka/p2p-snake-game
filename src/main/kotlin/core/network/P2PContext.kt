package d.zhdanov.ccfit.nsu.core.network

import core.network.nethandlers.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.interaction.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.messages.types.AckMsg
import d.zhdanov.ccfit.nsu.core.interaction.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.network.nethandlers.MulticastNetHandler
import d.zhdanov.ccfit.nsu.core.network.utils.AbstractMessageTranslator
import d.zhdanov.ccfit.nsu.core.network.utils.ContextNodeFabricT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageUtilsT
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import kotlinx.coroutines.Dispatchers
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap


class P2PContext<
    MessageT, InboundMessageTranslator : AbstractMessageTranslator<MessageT>
    >(
  contextJoinBacklog: Int,
  private val pingDelay: Long,
  private val resendDelay: Long,
  private val thresholdDelay: Long,
  private val messageTranslator: InboundMessageTranslator,
  private val contextNodeFabric: ContextNodeFabricT<MessageT, InboundMessageTranslator>,
  messageUtils: MessageUtilsT<MessageT, MessageType>
) {
  private val joinQueueIsFull =
    "The join queue is full. Please try again later."
  private val joinNotForMaster = "Join request allowed for master node."

  private val contextNodeDispatcher = Dispatchers.IO
  private val messageComparator = messageUtils.getComparator()
  private val unicastNetHandler: UnicastNetHandler = TODO()
  private val multicastNetHandler: MulticastNetHandler = TODO()

  //  в обычном режиме храним только мастера, в режиме сервера храним уже всех
  private val connectionsInfo: ConcurrentHashMap<
      InetSocketAddress, Node<MessageT, InboundMessageTranslator>
      > = TODO("нужно добавить время последнего действия в мапку ?")
  private val joinQueue: ArrayBlockingQueue<
      Node<MessageT, InboundMessageTranslator>
      > = ArrayBlockingQueue(contextJoinBacklog)

  @Volatile
  private var state: NodeRole = NodeRole.NORMAL

  fun handleUnicastMessage(
    message: MessageT, inetSocketAddress: InetSocketAddress
  ) {
    val msgT = messageTranslator.getMessageType(message);
    if (msgT == MessageType.AckMsg) {
      connectionsInfo[inetSocketAddress]?.let { state ->
        { state.approveMessage(message) }
      }
    } else if (state == NodeRole.MASTER) {
      masterHandle(message, inetSocketAddress);
    } else {

    }
    TODO("блять а что мне сделать чтобы все нормально обрабатывалось")
  }

  private fun sendMessage(
    message: GameMessage, inetSocketAddress: InetSocketAddress
  ) {

    if (MessageUtils.needToApprove(message)) {

    }
  }

  private fun masterHandle(
    message: MessageT, address: InetSocketAddress
  ) {
    val msgT = messageTranslator.getMessageType(message)
    if (msgT == MessageType.JoinMsg) {
      val msg = (messageTranslator.fromMessageT(message, msgT)) as JoinMsg
      joinNode(address, msg)
      return
    }

    if (MessageUtils.needToApprove(message)) {
      approveMessage(message, address)
    }
    applyMessage(message)
  }

  private fun joinNode(ipAddress: InetSocketAddress, message: JoinMsg) {
    if (message.nodeRole != NodeRole.NORMAL
      && message.nodeRole != NodeRole.VIEWER
    ) {
      return
    }

    val contextNode = connectionsInfo[ipAddress]
    if (contextNode != null) {
      return
    }

    val newNode = contextNodeFabric.create(
      ipAddress, message.msgSeq, message.nodeRole, pingDelay,
      resendDelay, thresholdDelay, this@P2PContext,
      messageComparator, contextNodeDispatcher
    )
    connectionsInfo[ipAddress] = newNode;
    val ret = joinQueue.offer(newNode)
    if (!ret) {
      nodeOffered(newNode)
    } else {
      retryJoinLater(newNode)
    }
  }

  private fun nodeOffered(node: Node<MessageT, InboundMessageTranslator>) {

  }

  private fun retryJoinLater(node: Node<MessageT, InboundMessageTranslator>) {

  }

  private fun applyMessage(message: GameMessage) {
    TODO("Not yet implemented")
  }

  private fun onRoleChanged(role: NodeRole) {
    this.state = role
  }

  private fun approveMessage(message: GameMessage, address: InetSocketAddress) {
    val ackMsg = AckMsg(message.msgSeq)
    unicastNetHandler.sendMessage(ackMsg, address)
  }

  fun onNodeDead(node: Node) {
    TODO("implement me please bitch")
  }
}