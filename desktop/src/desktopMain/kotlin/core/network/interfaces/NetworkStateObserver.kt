package d.zhdanov.ccfit.nsu.core.network.interfaces

interface NetworkStateObserver {
  fun handleNodeDetach(node: NodeT)
}
