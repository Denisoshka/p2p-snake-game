package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalUnacknowledgedMessagesGetAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.nodes.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.net.InetSocketAddress

interface NodeT {
  val nodeId: Int
  val ipAddress: InetSocketAddress
  var payload: NodePayloadT?
  val nodeState: NodeState
  val running: Boolean

  fun shutdown()
  fun ackMessage(message: SnakesProto.GameMessage): SnakesProto.GameMessage?
  fun addMessageForAck(message: SnakesProto.GameMessage)
  fun addAllMessageForAck(messages: List<SnakesProto.GameMessage>)
  fun CoroutineScope.startObservation(): Job

  /**
   * @throws IllegalUnacknowledgedMessagesGetAttempt if [Node.nodeState] <
   * [NodeT.NodeState.Disconnected]
   * */
  fun getUnacknowledgedMessages(): List<SnakesProto.GameMessage>

  enum class NodeState {
    Active,
    Passive,
    Disconnected,
    Terminated,
  }

  enum class NodeEvent {
    ShutdownFromCluster,
    ShutdownNowFromCluster,
    ShutdownFinishedFromCluster,
    ShutdownFromUser,
  }
}