package core.network.core.connection.game

import core.network.core.connection.Node
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.NodePayloadT

interface ClusterNodeT<T> : Node<T> {
  val name: String
  val payload: NodePayloadT?
}