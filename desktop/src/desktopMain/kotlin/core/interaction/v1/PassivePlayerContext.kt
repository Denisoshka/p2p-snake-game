package d.zhdanov.ccfit.nsu.core.interaction.v1

import core.network.core.Node
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg

class PassivePlayerContext(
  private val node: Node,
  private val name: String
) : NodePayloadT {
  override fun handleEvent(event: SteerMsg, seq: Long) {}

  override fun onContextObserverTerminated() {}

  override fun shootNodeState(state: StateMsg) {
    if(!node.running) return
    val pl = GamePlayer(
      name,
      node.id,
      node.ipAddress.address.hostAddress,
      node.ipAddress.port,
      node.nodeRole,
      PlayerType.HUMAN,
      0,
    )
    state.players.add(pl)
  }
}