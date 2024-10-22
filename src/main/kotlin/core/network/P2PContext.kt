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
import java.util.concurrent.atomic.AtomicReference

class P2PContext<MessageT, InboundMessageTranslator : AbstractMessageTranslator<MessageT>>(
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
  private val masterNode: AtomicReference<Node<MessageT, InboundMessageTranslator>>
  private val deputyNode: AtomicReference<Node<MessageT, InboundMessageTranslator>>

  //  в обычном режиме храним только мастера, в режиме сервера храним уже всех
  //  в обычном режиме мы тут храним только мастера и себя, когда заметили
  private val nodes: ConcurrentHashMap<InetSocketAddress, Node<MessageT, InboundMessageTranslator>> =
    TODO("нужно добавить время последнего действия в мапку ?")

  private val joinQueue: ArrayBlockingQueue<Node<MessageT, InboundMessageTranslator>> =
    ArrayBlockingQueue(contextJoinBacklog)

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
      nodes[inetSocketAddress]?.approveMessage(message)
      return
    }

    if (state == NodeRole.MASTER) {
      masterHandleMessage(message, inetSocketAddress);
    } else {
      handleMessage(message, inetSocketAddress)
    }
    TODO(
      "в роли обычного просматривающего " + "нужно дождаться первого state " + "и только после этого играть?"
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
    message: GameMessage, node: Node<MessageT, InboundMessageTranslator>
  ) {
    val msgT = message.msg.type
    val msg = messageTranslator.toMessageT(message, msgT)
    sendMessage(msg, node)
  }

  fun sendMessage(
    message: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) {
    val msgT = messageTranslator.getMessageType(message)
//    todo fix this
    if (localNode.address == masterNode.address) {
//      todo как обрабатывать сообщения которые мы отправили сами себе?
      val msg = messageTranslator.fromMessageT(message, msgT)
      applyMessage(msg)
    } else {

    }
    TODO("TODO как обрабатывать сообщение которое мы отправили сами себе")
  }

  fun sendUnicast(
    msg: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) {
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
      message.msgSeq,
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

  private fun onRoleChanged(role: NodeRole) {
    this.state = role
  }

  private fun acknowledgeMessage(
    message: MessageT, node: Node<MessageT, InboundMessageTranslator>
  ) {
    val ackMsg = messageUtils.getAckMsg(message, localNode.nodeId, node.nodeId);
    unicastNetHandler.sendMessage(ackMsg, node.address)
  }

  private fun sendMulticast() {}

  //  todo сделать что написано у ипполитова
  fun onNodeDead(node: Node<MessageT, InboundMessageTranslator>) {
//      TODO а других нод и быть не может в состоянии когда мы viewer?
    if (state == NodeRole.MASTER) {
      if (node.nodeRole == NodeRole.DEPUTY) masterOnDeputyDead(node)

    } else if (state == NodeRole.DEPUTY) {
      if (node.nodeRole == NodeRole.MASTER) deputyOnMasterDead(node)
    } else {
      if (node.nodeRole == NodeRole.MASTER) normalOnMasterDead(node)
    }
  }

  private fun deputyOnMasterDead(master: Node<MessageT, InboundMessageTranslator>) {
    nodes.remove(master.address)
    master.close()

    chooseNewDeputy()
    val unacknowledgedMessages = master.getUnacknowledgedMessages()
    for (msg in unacknowledgedMessages) {
      val gMsg = messageTranslator.fromMessageT(msg)
      applyMessage(gMsg)
    }
  }

  private fun normalOnMasterDead(oldMaster: Node<MessageT, InboundMessageTranslator>) {
    nodes.remove(oldMaster.address)
    oldMaster.close()
    val unacknowledgedMessages = oldMaster.getUnacknowledgedMessages()

    for (message in unacknowledgedMessages) {
      val address = deputyNode.address
      deputyNode.addMessageForAcknowledge(message)
      unicastNetHandler.sendMessage(message, address)
    }
  }

  //  todo как обрабатывать ситуацию когда у нас умерли все ноды и нет больше
//   кандидатов
  private fun masterOnDeputyDead(
    oldDeputy: Node<MessageT, InboundMessageTranslator>
  ) {
    val newDeputy = chooseNewDeputy(oldDeputy);
    newDeputy ?: return
  }

  //todo нужно обработать ситуацию когда мы начинаем игру
  private fun nodeFitToDeputy(
    node: Node<MessageT, InboundMessageTranslator>
  ): Boolean {
    return node.nodeState != Node.NodeState.Dead && node.nodeRole == NodeRole.NORMAL
  }

  private fun chooseNewDeputy(
    oldDeputy: Node<MessageT, InboundMessageTranslator>
  ): Node<MessageT, InboundMessageTranslator>? {
    nodes.remove(oldDeputy.address)
    oldDeputy.close()

    for ((_, node) in nodes) {
      if (nodeFitToDeputy(node)) {
        val ret = synchronized(node) {
          if (nodeFitToDeputy(node)) {
            if (deputyNode.compareAndSet(oldDeputy, node)) {
              node.nodeRole = NodeRole.DEPUTY
              return@synchronized node
            } else {
              return@synchronized oldDeputy
            }
          }
          return@synchronized null
        }
        if (ret == oldDeputy) return null
        else if (ret != null) return ret
      }
    }
    return null
  }

  /**
   * Attempts to set the given node as the new deputy node.
   *
   * @param newDeputy The node to attempt to set as the new deputy. The node must not be in the "Dead" state
   * and must currently have the `NORMAL` role for the operation to succeed.
   * @return `true` if the node was successfully set as the deputy; `false` otherwise.
   */
}