package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.connection.lobby.impl.NetNode
import core.network.core.connection.lobby.impl.NetNodeHandler
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.states.LobbyStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import kotlinx.coroutines.CoroutineScope
import java.net.InetSocketAddress

class LobbyState(
  private val ncStateMachine: NetworkStateMachine,
  private val controller: NetworkController,
  private val netNodesHandler: NetNodeHandler,
) : LobbyStateT {
  override fun CoroutineScope.sendJoinMsg(
    event: Event.ControllerEvent.JoinReq
  ) {
    val addr = InetSocketAddress(
      event.gameAnnouncement.host, event.gameAnnouncement.port
    )
    
    val node = netNodesHandler[addr] ?: run {
      val resendDelay = getResendDelay(
        event.gameAnnouncement.announcement.gameConfig.stateDelayMs
      )
      val thresholdDelay = getThresholdDelay(
        event.gameAnnouncement.announcement.gameConfig.stateDelayMs
      )
      val newNode = NetNode(
        context = netNodesHandler,
        ipAddress = addr,
        resendDelay = resendDelay.toLong(),
        thresholdDelay = thresholdDelay.toLong(),
      )
      netNodesHandler.registerNode(newNode)
    }
    val msg = MessageUtils.MessageProducer.getJoinMsg(
      msgSeq = ncStateMachine.nextSeqNum,
      playerType = SnakesProto.PlayerType.HUMAN,
      playerName = event.playerName,
      gameName = event.gameAnnouncement.announcement.gameName,
      nodeRole = MessageUtils.MessageProducer.nodeRoleToProto(
        event.playerRole
      )
    )
    node.sendToNode(msg)
    node.addMessageForAck(msg)
  }
  
  override fun pingHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) = ncStateMachine.onPingMsg(ipAddress, message, 0)
  
  override fun ackHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    if(ncStateMachine.networkState !is LobbyState) return
    val node = netNodesHandler[ipAddress] ?: return
    val onAckMsg = node.ackMessage(message) ?: return
    if(onAckMsg.typeCase == SnakesProto.GameMessage.TypeCase.JOIN){
    
    }
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
  
  override fun cleanup() {
    netNodesHandler.shutdown()
  }
  
  companion object LobbyStateDelayProvider {
    private const val MAX_THRESHOLD_COEF = 3.0
    private const val MAX_RESEND_DELAY_COEF = 0.1
    
    fun getResendDelay(stateDelay: Int): Double {
      return stateDelay * MAX_RESEND_DELAY_COEF
    }
    
    fun getThresholdDelay(stateDelay: Int): Double {
      return stateDelay * MAX_THRESHOLD_COEF
    }
  }
}