package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.states.node.Node
import java.net.InetSocketAddress

open class ObserverContext(
  override val node: Node,
  override val name: String,
) : NodePayloadT {
  override val score: Int
    get() = 0

  override fun handleEvent(event: SteerMsg, seq: Long) {}

  override fun onContextObserverTerminated() {}

  override fun shootContextState(
    state: StateMsg,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?
  ) {

    val nodeRole = getNodeRole(masterAddrId, deputyAddrId) ?: return
    val pl = GamePlayer(
      name,
      node.nodeId,
      node.ipAddress.address.hostAddress,
      node.ipAddress.port,
      nodeRole,
      PlayerType.HUMAN,
      score,
    )
    state.players.add(pl)
  }
}