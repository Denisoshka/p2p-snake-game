package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.connection.Node
import core.network.core.connection.game.ClusterNodeT
import core.network.core.connection.game.impl.ClusterNode
import core.network.core.connection.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.GameContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.LocalObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.GameStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.MasterStateT
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(MasterState::class.java.name)
private const val JoinInUpdateQ = 10

class MasterState(
  override val gameConfig: InternalGameConfig,
  gamePlayerInfo: GamePlayerInfo,
  private val gameEngine: GameContext,
  private val stateHandler: NetworkStateHolder,
  private val clusterNodesHandler: ClusterNodesHandler,
  private val nodesInitScope: CoroutineScope,
  val player: LocalObserverContext,
) : MasterStateT, GameStateT {
  val nodeId: Int
  
  init {
    Logger.info { "$this init" }
    nodeId = gamePlayerInfo.playerId
    gameEngine.launch()
  }
  
  override fun joinHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    val joinMsg = message.join
    try {
      MessageUtils.Preconditions.checkJoin(joinMsg)
    } catch(e: Exception) {
      val err = MessageUtils.MessageProducer.getErrorMsg(
        stateHandler.nextSeqNum, e.message ?: ""
      )
      stateHandler.sendUnicast(err, ipAddress)
      Logger.error(e) { "during joinHandle" }
      return
    }
    
    try {
      clusterNodesHandler[ipAddress]?.let {
        return
      }
      val initialNodeState = when(joinMsg.requestedRole) {
        SnakesProto.NodeRole.NORMAL -> Node.NodeState.Passive
        else                        -> Node.NodeState.Active
      }
      ClusterNode(
        nodeState = initialNodeState,
        nodeId = stateHandler.nextNodeId,
        ipAddress = ipAddress,
        clusterNodesHandler = clusterNodesHandler,
        name = joinMsg.playerName,
      ).apply { clusterNodesHandler.registerNode(this) }
    } catch(e: Exception) {
      Logger.error(e) { "during node registration" }
    }
  }
  
  override fun pingHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) = stateHandler.onPingMsg(ipAddress, message, nodeId)
  
  override fun ackHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) = stateHandler.nonLobbyOnAck(ipAddress, message, msgT)
  
  
  override fun roleChangeHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    if(!MessageUtils.RoleChangeIdentifier.fromNodeNodeLeave(message)) return
    clusterNodesHandler[ipAddress]?.apply {
      val ack = MessageUtils.MessageProducer.getAckMsg(
        message.msgSeq, nodeId, this.nodeId
      )
      sendToNode(ack)
      detach()
    }
  }
  
  override fun errorHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
  }
  
  override fun steerHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    clusterNodesHandler[ipAddress]?.apply {
      payload?.handleEvent(inp2p.msg as SteerMsg, inp2p.msgSeq)
    }
  }
  
  fun submitSteerMsg(steerMsg: SteerMsg) {
    player.handleEvent(
      steerMsg, stateHandler.nextSeqNum
    )
  }
  
  override fun cleanup() {
    Logger.info { "$this cleanup" }
    gameEngine.shutdown()
    nodesInitScope.cancel()
  }
  
  
  private fun findNewDeputy(
    oldDeputyId: Int
  ): Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?> {
    val (masterInfo, _) = stateHandler.masterDeputy
      ?: throw IllegalChangeStateAttempt("current master deputy absent")
    
    val deputyCandidate = clusterNodesHandler.find {
      it.value.nodeState == Node.NodeState.Passive && it.value.payload != null && it.value.nodeId != oldDeputyId
    }?.value
    
    val newDeputyInfo = deputyCandidate?.let {
      Pair(it.ipAddress, it.nodeId)
    }
    return (masterInfo to newDeputyInfo)
  }
  
  override fun handleNodeDetach(
    node: ClusterNodeT<Node.MsgInfo>, changeAccessToken: Any
  ) {
    stateHandler.apply {
      val (_, depInfo) = masterDeputy ?: return
      if(node.nodeId != depInfo?.second && node.ipAddress == depInfo?.first) return
      val (ms, newDep) = findNewDeputy(node.nodeId)
      
      /**
       * choose new deputy
       */
      val outMsg = MessageUtils.MessageProducer.getRoleChangeMsg(
        msgSeq = nextSeqNum,
        senderId = internalNodeId,
        receiverId = newDep.nodeId,
        senderRole = SnakesProto.NodeRole.MASTER,
        receiverRole = SnakesProto.NodeRole.DEPUTY
      )
      
      stateHandler.sendUnicast(outMsg, newDep.ipAddress)
    }
  }
}
