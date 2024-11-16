package d.zhdanov.ccfit.nsu.core.network.interfaces

import java.net.InetSocketAddress

interface NodeT {
  var nodeState: NodeState
  val id: Int
  val ipAddress: InetSocketAddress

  enum class NodeState {
    Active,
    Listening,
  }

  enum class NodeEvent {
    NodeRegistered,
    ShutdownFromCluster,
    ShutdownNowFromCluster,
    ShutdownFinishedFromCluster,
    ShutdownFromUser,
  }
}