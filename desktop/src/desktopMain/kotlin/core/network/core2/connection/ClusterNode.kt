package d.zhdanov.ccfit.nsu.core.network.core2.connection

import d.zhdanov.ccfit.nsu.SnakesProto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.net.InetSocketAddress

interface ClusterNode {
  val nodeId: Int
  val ipAddress: InetSocketAddress
  val lastReceive: Long
  val lastSend: Long
  val nodeState: NodeState
  val nodeHolder: ClusterNodesHolder
  fun sendToNode(msg: SnakesProto.GameMessage)
  fun sendToNodeWithAck(msg: SnakesProto.GameMessage)
  fun ackMessage(message: SnakesProto.GameMessage): SnakesProto.GameMessage?
  fun addMessageForAck(message: SnakesProto.GameMessage)
  fun addAllMessageForAck(messages: Iterable<SnakesProto.GameMessage>)
  fun CoroutineScope.startObservation(): Job
  fun markAsPassive()
  fun shutdown()
  fun getUnacknowledgedMessages(): List<SnakesProto.GameMessage>
  enum class NodeState {
    Active,
    Passive,
    Terminated,
  }
  
  data class MsgInfo(
    val req: SnakesProto.GameMessage, var lastCheck: Long
  )
}