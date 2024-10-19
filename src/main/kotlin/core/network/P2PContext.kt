package d.zhdanov.ccfit.nsu.core.network

import core.network.nethandlers.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.interaction.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.messages.types.AckMsg
import d.zhdanov.ccfit.nsu.core.interaction.messages.types.ErrorMsg
import d.zhdanov.ccfit.nsu.core.interaction.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.network.nethandlers.MulticastNetHandler
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap

class P2PContext<
    MessageT,
    InboundMessageTranslator : AbstractMessageTranslator<MessageT>
    > {
  private val joinQueueIsFull =
    "The join queue is full. Please try again later."
  private val joinNotForMaster = "Join request allowed for master node."

  private val messageTranslator: InboundMessageTranslator = TODO()
  private val unicastNetHandler: UnicastNetHandler = TODO()
  private val multicastNetHandler: MulticastNetHandler = TODO()

  //  в обычном режиме храним только мастера, в режиме сервера храним уже всех
  private val connectionsInfo: ConcurrentHashMap<
      InetSocketAddress,
      ContextNode<MessageT, InboundMessageTranslator>
      > = TODO("нужно добавить время последнего действия в мапку ?")
  private val joinQueue: ArrayBlockingQueue<InetSocketAddress>

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
    TODO()
    if (MessageUtils.needToApprove(message)) {

    }
  }

  private fun masterHandle(
    message: MessageT, inetSocketAddress: InetSocketAddress
  ) {
    val msgT = messageTranslator.getMessageType(message)
    if (msgT == MessageType.JoinMsg) {
      if (!joinQueue.offer(inetSocketAddress)) {
        sendMessage(ErrorMsg(joinQueueIsFull), inetSocketAddress)
      }
      return
    }

    if (MessageUtils.needToApprove(message)) {
      approveMessage(message, inetSocketAddress)
    }
    applyMessage(message)
  }

  private fun joinNode(ipAddress: InetSocketAddress, message: JoinMsg) {
    if (state != NodeRole.MASTER) {
      sendMessage(ErrorMsg(), ipAddress);
      TODO(
        "что делать с теми кто не присоединился, " +
            "мы же им кидаем ошибку, но они не могут зайти в игру"
      )
      return;
    }

  }

  private fun deleteNode()

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

  fun onNodeDead(node: ContextNode) {
    TODO("implement me please bitch")
  }
}