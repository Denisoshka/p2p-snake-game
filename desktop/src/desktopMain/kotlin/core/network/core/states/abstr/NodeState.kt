package d.zhdanov.ccfit.nsu.core.network.core.states.abstr

import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import java.net.InetSocketAddress


interface NodeState {
  fun atNodeDetachPostProcess(
    node: ClusterNodeT<Node.MsgInfo>,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>?,
    changeAccessToken: Any
  )
  
  fun cleanup()
  fun toLobby(
    event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
  )
}
