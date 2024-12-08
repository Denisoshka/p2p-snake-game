package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.states.node.lobby.impl.NetNodeHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.states.NetworkStateT
import java.net.InetSocketAddress

class LobbyState(
  private val ncStateMachine: NetworkStateMachine,
  private val controller: NetworkController,
  private val netNodesHandler: NetNodeHandler,
) : NetworkStateT {

  override fun ackHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    if(ncStateMachine.networkState !is LobbyState) return
    val node = netNodesHandler[ipAddress] ?: return
    val msg = node.ackMessage(message) ?: return
  }

  override fun announcementHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
  }

  override fun errorHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {

  }

  override fun submitSteerMsg(steerMsg: SteerMsg) {/*not handle in this state lol*/
  }

  override fun cleanup() {
    TODO("implement me please")
  }
}