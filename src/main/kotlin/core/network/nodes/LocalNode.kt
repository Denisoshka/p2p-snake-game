package core.network.nodes

import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import java.net.InetSocketAddress

class LocalNode(
  override var nodeState: NodeT.NodeState,
  override val id: Int,
  override val address: InetSocketAddress,
) : NodeT<InetSocketAddress>