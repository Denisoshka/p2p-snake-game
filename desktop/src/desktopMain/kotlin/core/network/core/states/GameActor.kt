package d.zhdanov.ccfit.nsu.core.network.core.states

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import java.net.InetSocketAddress

interface GameActor : BaseActor {
  fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage,
  )
  
  fun steerHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage,
  )
  
  fun stateHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage,
  )
  
  fun joinHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage,
  )
  
  fun processDetachedNode(node: ClusterNodeT<Node.MsgInfo>)
}