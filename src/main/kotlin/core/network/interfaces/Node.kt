package d.zhdanov.ccfit.nsu.core.network.interfaces

interface Node<AddressT> {
  val id: Int
  val address: AddressT
}