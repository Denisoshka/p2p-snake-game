package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.connection.Node
import core.network.core.connection.game.impl.ClusterNode
import core.network.core.connection.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.GameContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.LocalObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.ObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.GameStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.MasterStateT
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(MasterState::class.java.name)
private const val PlayerNameIsBlank = "player name blank"
private const val JoinInUpdateQ = 10

class MasterState(
  override val gameConfig: InternalGameConfig,
  gamePlayerInfo: GamePlayerInfo,
  private val gameEngine: GameContext,
  private val stateMachine: NetworkStateHolder,
  private val netController: NetworkController,
  private val clusterNodesHandler: ClusterNodesHandler,
  val player: LocalObserverContext,
//  gamePlayerInfo: GamePlayerInfo,
//  state: SnakesProto.GameMessage.StateMsg? = null,
) : MasterStateT, GameStateT {
  val nodeId: Int
  private val nodesInitScope: CoroutineScope = CoroutineScope(
    Dispatchers.Default
  )
  
  init {
    Logger.info { "$this init" }
    nodeId = player.
  }
  
  override fun joinHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    try {
      val joinMsg = message.join
      if(joinMsg.playerName.isBlank()) {
        throw IllegalNodeRegisterAttempt(PlayerNameIsBlank)
      }
      if(!(joinMsg.requestedRole == SnakesProto.NodeRole.VIEWER || joinMsg.requestedRole == SnakesProto.NodeRole.NORMAL)) {
        throw IllegalNodeRegisterAttempt(
          "$ipAddress invalid node role: ${joinMsg.requestedRole}"
        )
      }
      if(clusterNodesHandler[ipAddress] != null) {
        return
      }
      val initialNodeState = when(joinMsg.requestedRole) {
        SnakesProto.NodeRole.NORMAL -> Node.NodeState.Active
        else                        -> Node.NodeState.Passive
      }
      
      val node = ClusterNode(
        nodeState = initialNodeState,
        nodeId = stateMachine.nextNodeId,
        ipAddress = ipAddress,
        payload = null,
        clusterNodesHandler = clusterNodesHandler,
      )
      
      if(node.nodeState == Node.NodeState.Passive) {
        node.payload = ObserverContext(node, joinMsg.playerName)
      } else {
        TODO()
      }
      
      try {
        clusterNodesHandler.registerNode(node)
      } catch(e: IllegalNodeRegisterAttempt) {
        Logger.error(e) { "during registerNode" }
      }
    } catch(e: IllegalNodeRegisterAttempt) {
      val err = MessageUtils.MessageProducer.getErrorMsg(
        stateMachine.nextSeqNum, e.message ?: ""
      )
      stateMachine.sendUnicast(err, ipAddress)
      Logger.error(e) { "during joinHandle" }
    }
  }
  
  override fun pingHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) = stateMachine.onPingMsg(ipAddress, message, nodeId)
  
  override fun ackHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) = stateMachine.nonLobbyOnAck(ipAddress, message, msgT)
  
  
  override fun roleChangeHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    if(!MessageUtils.RoleChangeIdentifier.fromNodeNodeLeave(message)) return
    val node = clusterNodesHandler[ipAddress] ?: return
    
    val ack = MessageUtils.MessageProducer.getAckMsg(
      message.msgSeq, stateMachine.internalNodeId, node.nodeId
    )
    netController.sendUnicast(ack, ipAddress)
    
    if(!node.running) node.detach()
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
    val node = clusterNodesHandler[ipAddress] ?: return
    if(!node.running) return
    
    val inp2p = MessageTranslator.fromProto(
      message, MessageType.SteerMsg
    )
    node.payload?.handleEvent(inp2p.msg as SteerMsg, inp2p.msgSeq)
  }
  
  fun submitSteerMsg(steerMsg: SteerMsg) {
    player.handleEvent(
      steerMsg, stateMachine.nextSeqNum
    )
  }
  
  override fun cleanup() {
    Logger.info { "$this cleanup" }
    gameEngine.shutdown()
    nodesInitScope.cancel()
  }
  
  override suspend fun handleNodeDetach(
    node: ClusterNode
  ) {
    stateMachine.apply {
      val (_, depInfo) = masterDeputy ?: return
      if(node.nodeId != depInfo?.second && node.ipAddress == depInfo?.first) return
      val newDep = chooseSetNewDeputy(node.nodeId) ?: return
      
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
      
      netController.sendUnicast(outMsg, newDep.ipAddress)
    }
  }
}
