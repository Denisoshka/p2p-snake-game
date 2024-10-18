package d.zhdanov.ccfit.nsu.core.network

import core.network.nethandlers.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.interaction.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.messages.types.AckMsg
import d.zhdanov.ccfit.nsu.core.interaction.messages.types.ErrorMsg
import d.zhdanov.ccfit.nsu.core.network.nethandlers.MulticastNetHandler
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap

class P2PContext {
  private val joinQueueIsFull =
    "The join queue is full. Please try again later."

  private val unicastNetHandler: UnicastNetHandler = TODO()
  private val multicastNetHandler: MulticastNetHandler = TODO()

  //  в обычном режиме храним только мастера, в режиме сервера храним уже всех
  private val connectionsInfo: ConcurrentHashMap<InetSocketAddress,
      ContextNode> =
    TODO("нужно добавить время последнего действия в мапку ?")
  private val mes: EnumMap<MessageType, Int> = EnumMap(MessageType::class.java)
  private val joinQueue: ArrayBlockingQueue<InetSocketAddress>

  @Volatile
  private var state: NodeRole = NodeRole.NORMAL
  fun handleUnicastMessage(
    message: GameMessage, inetSocketAddress: InetSocketAddress
  ) {
    if (message.messageType == MessageType.AckMsg) {
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
    message: GameMessage, inetSocketAddress: InetSocketAddress
  ) {
    if (message.messageType == MessageType.JoinMsg) {
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