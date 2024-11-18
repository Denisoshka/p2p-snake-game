package d.zhdanov.ccfit.nsu.core.network.core.states

import core.network.core.NodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import java.net.InetSocketAddress

class LobbyState(
  private val ncStateMachine: NetworkStateMachine,
  private val controller: NetworkController,
  private val nodesHandler: NodesHandler,
) : NetworkState {
  override fun ackHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
    val p2pmsg = ncStateMachine.msgTranslator.fromMessageT(
      message, MessageType.AckMsg
    )

  }

  override fun announcementHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {

    TODO("Not yet implemented")
  }

  override fun errorHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
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