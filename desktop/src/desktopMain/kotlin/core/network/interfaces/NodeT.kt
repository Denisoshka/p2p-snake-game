package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import java.net.InetSocketAddress

interface NodeT {
  var nodeRole: NodeRole
  var nodeState: NodeState
  val id: Int
  val ipAddress: InetSocketAddress


  enum class NodeState {
    Active,
    Passive,
    Disconnected,
  }

  enum class NodeEvent {
    NodeRegistered,
    ShutdownFromCluster,
    ShutdownNowFromCluster,
    ShutdownFinishedFromCluster,
    ShutdownFromUser,
  }
}