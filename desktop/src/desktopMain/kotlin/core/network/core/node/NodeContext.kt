package d.zhdanov.ccfit.nsu.core.network.core.node

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import java.net.InetSocketAddress

interface NodeContext<NodeT> {
  val launched: Boolean
  val nextSeqNum: Long

  fun launch()
  fun shutdown()

  fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  )

  /**
   * @throws IllegalNodeRegisterAttempt
   * */
  fun registerNode(node: NodeT): NodeT

  operator fun get(ipAddress: InetSocketAddress): NodeT?

  suspend fun handleNodeTermination(node: NodeT)
  suspend fun handleNodeDetach(node: NodeT)
}