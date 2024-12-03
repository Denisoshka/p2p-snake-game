package d.zhdanov.ccfit.nsu.core.network.interfaces

import core.network.core.Node
import d.zhdanov.ccfit.nsu.SnakesProto
import java.net.InetSocketAddress

interface NodeContext {
  fun shutdown()

  fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  )

  fun addNewNode(
    ipAddress: InetSocketAddress, registerInContext: Boolean = true
  ): Node

  suspend fun handleNodeRegistration(
    node: Node
  )

  suspend fun handleNodeTermination(
    node: Node
  )

  suspend fun handleNodeDetachPrepare(
    node: Node
  )
}