package d.zhdanov.ccfit.nsu.core.network.core.node

interface ClusterNodeT<T> : Node<T> {
  val name: String
  val payload: NodePayloadT
  
  fun mountObservable()
}