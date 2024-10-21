package d.zhdanov.ccfit.nsu.core.network

import core.network.nethandlers.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.interaction.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.network.nethandlers.MulticastNetHandler
import d.zhdanov.ccfit.nsu.core.network.utils.AbstractMessageTranslator
import d.zhdanov.ccfit.nsu.core.network.utils.ContextNodeFabricT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageUtilsT
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
  private val messageUtils: MessageUtilsT<MessageT, MessageType>
) {
  private val joinQueueIsFull =
    "The join queue is full. Please try again later."
  private val joinNotForMaster = "Join request allowed for master node."

  private val contextNodeDispatcher = Dispatchers.IO
  private val messageComparator = messageUtils.getComparator()
  private val unicastNetHandler: UnicastNetHandler<MessageT> = TODO()
  private val multicastNetHandler: MulticastNetHandler<MessageT> = TODO()
  private var localNode: Node<MessageT, InboundMessageTranslator> = TODO()
  private var masterNode: Node<MessageT, InboundMessageTranslator> = TODO()
  private var deputyNode: Node<MessageT, InboundMessageTranslator> = TODO()

  //  в обычном режиме храним только мастера, в режиме сервера храним уже всех
  private val nodes: ConcurrentHashMap<
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
    val msgT = messageTranslator.getMessageType(message)
    if (msgT == MessageType.UnrecognisedMsg) {
      return
    }

    if (msgT == MessageType.AckMsg) {
      nodes[inetSocketAddress]?.let { state ->
        { state.approveMessage(message) }
      }
      return;
    }

    if (state == NodeRole.MASTER) {
      masterHandleMessage(message, inetSocketAddress);
    } else {
      handleMessage(message, inetSocketAddress)
    }
    TODO(
      "в роли обычного просматривающего " +
          "нужно дождаться первого state " +
          "и только после этого играть"
    )
  }

  private fun handleMessage(
    message: MessageT, inetSocketAddress: InetSocketAddress
  ) {
    if (state == NodeRole.DEPUTY) {

    } else {

    }
  }

  private fun sendMessage(
    message: GameMessage,
    address: InetSocketAddress
  ) {
    val node = nodes[address] ?: return

    val msgT = message.messageType
    val msg = messageTranslator.toMessageT(message, msgT)
    if (messageUtils.needToAcknowledge(msgT)) {
      node.addMessageForAcknowledge(msg)
    }
    unicastNetHandler.sendMessage(msg, address)
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
    if (message.nodeRole != NodeRole.NORMAL
      && message.nodeRole != NodeRole.VIEWER
    ) return
    nodes[ipAddress]?.let { return }

    val newNode = contextNodeFabric.create(
      ipAddress, message.msgSeq, message.nodeRole, pingDelay,
      resendDelay, thresholdDelay, this@P2PContext,
      messageComparator, contextNodeDispatcher
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
    message: MessageT,
    node: Node<MessageT, InboundMessageTranslator>
  ) {
    TODO("нужно дождаться ответа от движка и все")
  }

  private fun retryJoinLater(
    message: MessageT,
    node: Node<MessageT, InboundMessageTranslator>
  ) {
    val error = messageUtils.getErrorMsg(message, joinQueueIsFull)
    unicastNetHandler.sendMessage(error, node.address)
  }

  private fun applyMessage(message: GameMessage) {
    TODO("Not yet implemented")
  }

  private fun onRoleChanged(role: NodeRole) {
    this.state = role
  }


  private fun acknowledgeMessage(
    message: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) {
    val ackMsg = messageUtils.getAckMsg(message, localNode.nodeId, node.nodeId);
    unicastNetHandler.sendMessage(ackMsg, node.address)
  }

  fun onNodeDead(node: Node<MessageT, InboundMessageTranslator>) {
    val role = node.nodeRole
    if (role == NodeRole.NORMAL || role == NodeRole.VIEWER) {
      return
    }

    if ((state == NodeRole.MASTER || state == NodeRole.DEPUTY)) {
      if (role == NodeRole.DEPUTY) chooseNewDeputy(node)
    } else {
      if (role == NodeRole.MASTER) onMasterDead(node)
    }

    TODO("что будет если наша нода умрет?")
  }

  private fun onMasterDead(node: Node<MessageT, InboundMessageTranslator>) {

  }

  private fun chooseNewDeputy(node: Node<MessageT, InboundMessageTranslator>) {

  }
}