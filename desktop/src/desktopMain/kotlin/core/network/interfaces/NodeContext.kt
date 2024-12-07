package d.zhdanov.ccfit.nsu.core.network.interfaces

import core.network.core.Node
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
  fun registerNode(node: Node, registerInContext: Boolean = true): Node

  operator fun get(ipAddress: InetSocketAddress): Node?

  suspend fun handleNodeRegistration(node: Node)
  suspend fun handleNodeTermination(node: Node)
  suspend fun handleNodeDetachPrepare(node: Node)
}