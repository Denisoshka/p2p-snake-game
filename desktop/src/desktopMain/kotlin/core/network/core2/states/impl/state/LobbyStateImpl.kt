package d.zhdanov.ccfit.nsu.core.network.core2.states.impl.state

import core.network.core.connection.lobby.impl.NetNode
import core.network.core.states.utils.MasterStateUtils
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.network.core2.states.BaseActor
import d.zhdanov.ccfit.nsu.core.network.core2.states.Event
import d.zhdanov.ccfit.nsu.core.network.core2.states.NodeState
import d.zhdanov.ccfit.nsu.core.network.core2.states.StateHolder
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger { LobbyStateImpl::class.java }

class LobbyStateImpl(
  private val stateHolder: StateHolder,
) : NodeState.LobbyState, BaseActor {
  private val gameController = stateHolder.gameController
  private val nodesHolder = stateHolder.nodesHolder
  
  fun requestJoinToGame(
    event: Event.State.ByController.JoinReq
  ) {
    val addr = InetSocketAddress(
      event.gameAnnouncement.host, event.gameAnnouncement.port
    )
    
    val node = nodesHolder[addr] ?: run {
      val resendDelay = getResendDelay(
        event.gameAnnouncement.announcement.config.stateDelayMs
      )
      val thresholdDelay = getThresholdDelay(
        event.gameAnnouncement.announcement.config.stateDelayMs
      )
      val newNode = NetNode(
        context = nodesHolder,
        ipAddress = addr,
        resendDelay = resendDelay.toLong(),
        thresholdDelay = thresholdDelay.toLong(),
      )
      nodesHolder.registerNode(newNode)
    }
    val msg = MessageUtils.MessageProducer.getJoinMsg(
      msgSeq = stateHolder.nextSeqNum,
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
  ) = stateHolder.onPingMsg(ipAddress, message, me)
  
  override fun ackHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
  ) {
    val node = nodesHolder[ipAddress] ?: return
    val onAckMsg = node.ackMessage(message) ?: return
    if(onAckMsg.req.typeCase == SnakesProto.GameMessage.TypeCase.JOIN) {
      d.zhdanov.ccfit.nsu.core.network.node.connected.Logger.trace {
        "receive join ack from $ipAddress with id ${message.receiverId}"
      }
      
    }
  }
  
  
  override fun errorHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
  ) {
    val node = nodesHolder[ipAddress] ?: return
    val onAckMsg = node.ackMessage(message) ?: return
    if(onAckMsg.req.typeCase == SnakesProto.GameMessage.TypeCase.JOIN) {
      Logger.trace {
        "receive join error from $ipAddress with cause ${message.error.errorMessage}"
      }
    }
  }
  
  
  override fun toMaster(
    nodesHolder: ClusterNodesHolder,
    event: Event.State.ByController.LaunchGame
  ): NodeState {
    try {
      val playerInfo = GamePlayerInfo(event.internalGameConfig.playerName, 0)
      MasterStateUtils.prepareMasterContext(
        clusterNodesHolder = nodesHolder,
        gamePlayerInfo = playerInfo,
        gameConfig = event.internalGameConfig,
        stateHolder = stateHolder,
      ).apply {
        stateHolder.setupNewState(this, changeAccessToken)
      }
      gameController.openGameScreen(event.internalGameConfig)
    } catch(e: Exception) {
      Logger.error(e) { "unexpected error during launch game" }
      throw e
    }
  }
  
  override fun toActive(
    nodesHolder: ClusterNodesHolder,
    event: Event.State.ByInternal.JoinReqAck,
  ): NodeState {
    try {
      val destAddr = InetSocketAddress(
        event.onEventAck.gameAnnouncement.host,
        event.onEventAck.gameAnnouncement.port
      )
      val localNode = nodesHolder.launch(
        event.gamePlayerInfo.playerId, ClusterNode.NodeState.Passive
      )
      /** register master */
      this.nodesHolder.registerNode(
        ipAddress = destAddr,
        id = event.senderId,
        nodeRole = ClusterNode.NodeState.Active
      )
      gameController.openGameScreen(event.internalGameConfig)
      return ActiveStateImpl(
        localNode = localNode,
        stateHolder = stateHolder,
        gameConfig = event.internalGameConfig
      )
    } catch(e: Exception) {
      nodesHolder.shutdown()
      Logger.trace { "joined as ${SnakesProto.NodeRole.NORMAL} to $event" }
      throw e
    }
  }
  
  
  override fun toPassive(
    nodesHolder: ClusterNodesHolder,
    event: Event.State.ByInternal.JoinReqAck,
  ): NodeState {
    try {
      val destAddr = InetSocketAddress(
        event.onEventAck.gameAnnouncement.host,
        event.onEventAck.gameAnnouncement.port
      )
      val localNode = nodesHolder.launch(
        nodeId = event.gamePlayerInfo.playerId,
        nodeRole = ClusterNode.NodeState.Passive
      )
      /** register master */
      this.nodesHolder.registerNode(
        ipAddress = destAddr,
        id = event.senderId,
        nodeRole = ClusterNode.NodeState.Active
      )
      gameController.openGameScreen(event.internalGameConfig)
      return PassiveStateImpl(
        localNode = localNode,
        gameConfig = event.internalGameConfig,
        stateHolder = stateHolder
      )
    } catch(e: Exception) {
      Logger.error(e) { "unexpected error during switch to passive game" }
      nodesHolder.shutdown()
      throw e
    }
  }
  
  override fun atNodeDetachPostProcess(
    node: ClusterNode,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>?
  ): NodeState? {
    /**
     * not handle
     */
    return null
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