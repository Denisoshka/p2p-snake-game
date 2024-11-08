package d.zhdanov.ccfit.nsu.core.network.interfaces

interface NodeT<AddressT> {
  val id: Int
  val address: AddressT
}