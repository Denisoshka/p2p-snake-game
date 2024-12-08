package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeT

interface NetworkStateObserver {
  fun handleNodeDetach(node: NodeT)
}
