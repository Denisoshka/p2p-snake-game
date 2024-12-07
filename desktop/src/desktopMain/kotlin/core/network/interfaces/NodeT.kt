package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.interaction.v1.context.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import java.net.InetSocketAddress

interface NodeT {
  var nodeRole: NodeRole
  val id: Int
  val ipAddress: InetSocketAddress
  var payload: NodePayloadT?
  val nodeState: NodeState
  fun shutdown()
  fun launch()

  enum class NodeState {
    None,
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