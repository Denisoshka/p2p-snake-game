package d.zhdanov.ccfit.nsu.core.network.interfaces

interface NodeT<AddressT> {
  var nodeState: NodeState
  val id: Int
  val address: AddressT

  enum class NodeState {
    Active,
    Listening,
  }

  enum class NodeEvent {
    NodeRegistered,
    ShutdownFromCluster,
    ShutdownFinishedFromCluster,
    ShutdownFromUser,
  }
}