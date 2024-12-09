package d.zhdanov.ccfit.nsu.core.network.core.states.node

import d.zhdanov.ccfit.nsu.SnakesProto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.net.InetSocketAddress

interface Node {
  val nodeId: Int
  val ipAddress: InetSocketAddress
  val running: Boolean
  var lastReceive: Long
  var lastSend: Long
  val nodeState: NodeState

  fun sendToNode(msg: SnakesProto.GameMessage)
  fun ackMessage(message: SnakesProto.GameMessage): SnakesProto.GameMessage?
  fun addMessageForAck(message: SnakesProto.GameMessage)
  fun addAllMessageForAck(messages: List<SnakesProto.GameMessage>)
  fun CoroutineScope.startObservation(): Job
  fun detach()
  fun shutdown()

  fun getUnacknowledgedMessages(): List<SnakesProto.GameMessage>

  enum class NodeState {
    Active,
    Passive,
    Disconnected,
    Terminated,
  }

  data class MsgInfo(
    val msg: SnakesProto.GameMessage, var lastCheck: Long
  )
}