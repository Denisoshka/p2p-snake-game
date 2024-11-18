package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import java.net.InetSocketAddress

interface NodeT {
  var nodeRole: NodeRole
  val id: Int
  val ipAddress: InetSocketAddress


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