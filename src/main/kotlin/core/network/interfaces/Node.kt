package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole

interface Node<AddressT> {
  val nodeRole : NodeRole
  val id: Int
  val address: AddressT
}