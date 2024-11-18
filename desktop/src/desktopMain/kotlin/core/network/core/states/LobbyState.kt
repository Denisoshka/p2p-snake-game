package d.zhdanov.ccfit.nsu.core.network.core.states

import core.network.core.NodesHandler
import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import java.net.InetSocketAddress

class LobbyState<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
  private val ncStateMachine: NetworkStateMachine<MessageT, InboundMessageTranslator, Payload>,
  private val controller: NetworkController<MessageT, InboundMessageTranslator, Payload>,
  private val nodesHandler: NodesHandler<MessageT, InboundMessageTranslator, Payload>,
) : NetworkState<MessageT, InboundMessageTranslator, Payload> {
  override fun ackHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    val p2pmsg = ncStateMachine.msgTranslator.fromMessageT(
      message, MessageType.AckMsg
    )

  }

  override fun announcementHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {

    TODO("Not yet implemented")
  }

  override fun errorHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    nodesHandler.getNode(ipAddress)?.handleEvent(
      NodeT.NodeEvent.ShutdownNowFromCluster
    )
    ncStateMachine.waitToJoin.remove(ipAddress)
  }

  override fun submitSteerMsg(steerMsg: SteerMsg) {
    /*not handle in this state lol*/
  }


  override fun initialize() {
    ncStateMachine.latestGameState.set(null)
    ncStateMachine.masterDeputy.set(null)
  }

  override fun cleanup() {
    ncStateMachine.latestGameState.set(null)
    ncStateMachine.masterDeputy.set(null)

    TODO("Not yet implemented")
  }
}