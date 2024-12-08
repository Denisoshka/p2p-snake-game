package d.zhdanov.ccfit.nsu.core.network.core.states.node.game

import d.zhdanov.ccfit.nsu.core.interaction.v1.context.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeT

interface GameNodeT : NodeT {
  var payload: NodePayloadT?
  val nodeState: NodeState

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