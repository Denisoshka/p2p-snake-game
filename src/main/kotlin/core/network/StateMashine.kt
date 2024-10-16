package d.zhdanov.ccfit.nsu.core.network

import core.network.nethandlers.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.network.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.network.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.messages.types.Ack
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class StateMachine {
  private val nodeConfig: NodeConfig
  private val connections: ConcurrentHashMap<InetSocketAddress, ConnectionInfo> =
    ConcurrentHashMap<InetSocketAddress, ConnectionInfo>()
  private val unicastNetHandler: UnicastNetHandler = TODO()

  private val messageForApprove: ConcurrentHashMap<InetSocketAddress,
      MutableSet<GameMessage>> =
    TODO("нужно добавить время последнего действия в мапку ?")

  @Volatile
  private var state: NodeRole = NodeRole.NORMAL

  private val mes: EnumMap<MessageType, Int> = EnumMap(MessageType::class.java)

  fun handleMessage(
    message: GameMessage, inetSocketAddress: InetSocketAddress
  ) {
    if (state == NodeRole.MASTER || state == NodeRole.DEPUTY) {
      masterHandle(message, inetSocketAddress);
    } else if () {

    } else {

    }
    TODO()
  }

  private fun sendMessage(
    message: GameMessage, inetSocketAddress: InetSocketAddress
  ) {
    if (MessageUtils.needToApprove(message)) {

    }
  }

  private fun masterHandle(
    message: GameMessage, inetSocketAddress: InetSocketAddress
  ) {
    if (message.messageType == MessageType.AckMsg) {
      messageForApprove[inetSocketAddress]?.let { map ->
        synchronized(map) { map.remove(message) }
      }
      return
    }
    if (MessageUtils.needToApprove(message)) {
      approveMessage(message, inetSocketAddress)
    }
  }

  fun launchDeputyTask() {
  }

  fun lastApprovedMsg() {
  }

  fun changeDeputy(address: InetSocketAddress?) {
  }

  private fun onRoleChanged(role: NodeRole) {
    this.state = role
  }

  private fun onMasterRole() {
  }

  private fun onDeputyRole() {
  }

  private fun approveMessage(message: GameMessage, address: InetSocketAddress) {
    val ack = Ack(message.msgSeq)
    unicastNetHandler.sendMessage(ack, address)
  }
}
