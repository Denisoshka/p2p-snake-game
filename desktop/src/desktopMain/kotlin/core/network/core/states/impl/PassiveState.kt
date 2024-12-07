package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.states.nodes.NodesHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import java.net.InetSocketAddress

class PassiveState(
  private val ncStateMachine: NetworkStateMachine,
  private val controller: NetworkController,
  private val nodesHandler: NodesHandler,
) : NetworkState {
  override fun submitSteerMsg(steerMsg: SteerMsg) {}

  override fun pingHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = ncStateMachine.onPingMsg(ipAddress, message, msgT)

  override fun ackHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
    ncStateMachine.onAckMsg(ipAddress, message)
  }

  override fun stateHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  override fun errorHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
    TODO("Not yet implemented")
  }

  override fun cleanup() {
    nodesHandler.shutdown()
  }
}