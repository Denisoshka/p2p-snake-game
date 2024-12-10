package core.network.core.connection.game

import d.zhdanov.ccfit.nsu.core.interaction.v1.context.NodePayloadT
import core.network.core.connection.Node

interface ClusterNodeT<T> : Node<T> {
  var payload: NodePayloadT?
}