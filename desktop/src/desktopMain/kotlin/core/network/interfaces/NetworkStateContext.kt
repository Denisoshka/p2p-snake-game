package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.core.states.abstr.NodeState
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.UnicastNetHandler
import java.net.InetSocketAddress

interface NetworkStateContext : NodeState {
  val networkState: NodeState
  val unicastNetHandler: UnicastNetHandler
  
  suspend fun detachNode(node: ClusterNodeT<Node.MsgInfo>)
  suspend fun terminateNode(node: ClusterNodeT<Node.MsgInfo>)
  suspend fun joinNode(node: ClusterNodeT)
  
  fun sendUnicast(
    msg: GameMessage, nodeAddress: InetSocketAddress
  ) = unicastNetHandler.sendUnicastMessage(msg, nodeAddress)
  
  override fun joinHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkState.joinHandle(ipAddress, message, msgT)
  
  
  override fun pingHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkState.pingHandle(ipAddress, message, msgT)
  
  
  override fun ackHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkState.ackHandle(ipAddress, message, msgT)
  
  
  override fun stateHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkState.stateHandle(ipAddress, message, msgT)
  
  
  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkState.roleChangeHandle(ipAddress, message, msgT)
  
  
  override fun announcementHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkState.announcementHandle(ipAddress, message, msgT)
  
  
  override fun errorHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkState.errorHandle(ipAddress, message, msgT)
  
  
  override fun steerHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = networkState.steerHandle(ipAddress, message, msgT)
}