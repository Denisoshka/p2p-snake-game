package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import java.net.InetSocketAddress

interface NodeContext {
  val launched: Boolean

  fun launch()
  fun shutdown()

  fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  )

  /**
   * @throws IllegalNodeRegisterAttempt if node already in context
   * */
  fun registerNode(node: NodeT, registerInContext: Boolean = true): NodeT

  operator fun get(ipAddress: InetSocketAddress): NodeT?

  suspend fun handleNodeRegistration(node: NodeT)
  suspend fun handleNodeTermination(node: NodeT)
  suspend fun handleNodeDetachPrepare(node: NodeT)
}