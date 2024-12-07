package d.zhdanov.ccfit.nsu.core.network.interfaces

import core.network.core.Node
import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalUnacknowledgedMessagesGetAttempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.net.InetSocketAddress

interface NodeT {
  val id: Int
  val ipAddress: InetSocketAddress
  var payload: NodePayloadT?
  val nodeState: NodeState
  val running: Boolean
  fun shutdown()
  fun CoroutineScope.startObservation(): Job

  /**
   * @throws IllegalUnacknowledgedMessagesGetAttempt if [Node.nodeState] <
   * [NodeT.NodeState.Disconnected]
   * */
  fun getUnacknowledgedMessages(
    node: Node
  ): List<GameMessage>

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

  companion object {
    fun isRunning(state: NodeState): Boolean {
      return state == NodeState.Active || state == NodeState.Passive
    }
  }
}