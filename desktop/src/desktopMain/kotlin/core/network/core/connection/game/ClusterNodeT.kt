package core.network.core.connection.game

import d.zhdanov.ccfit.nsu.core.interaction.v1.context.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.core.states.node.Node

interface ClusterNodeT : Node {
  var payload: NodePayloadT?
}