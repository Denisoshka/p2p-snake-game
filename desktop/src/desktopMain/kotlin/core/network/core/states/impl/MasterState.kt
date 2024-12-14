package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.LocalNode
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.GameContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.MasterStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(MasterState::class.java.name)
private const val JoinInUpdateQ = 10

class MasterState(
  val localNode: LocalNode,
  override val internalGameConfig: InternalGameConfig,
  private val gameEngine: GameContext,
  private val stateHolder: NetworkStateHolder,
  private val nodesHolder: ClusterNodesHandler,
  private val nodesInitScope: CoroutineScope,
) : MasterStateT, GameStateT {
  
  init {
    Logger.info { "$this init" }
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
        stateHolder.nextSeqNum, e.message ?: ""
      )
      stateHolder.sendUnicast(err, ipAddress)
      Logger.error(e) { "during joinHandle" }
      return
    }
    
    try {
      nodesHolder[ipAddress]?.let {
        return
      }
      val initialNodeState = when(joinMsg.requestedRole) {
        SnakesProto.NodeRole.NORMAL -> Node.NodeState.Passive
        else                        -> Node.NodeState.Active
      }
      ClusterNode(
        nodeState = initialNodeState,
        nodeId = stateHolder.nextNodeId,
        ipAddress = ipAddress,
        clusterNodesHandler = nodesHolder,
        name = joinMsg.playerName,
      ).apply { nodesHolder.registerNode(this) }
    } catch(e: Exception) {
      Logger.error(e) { "during node registration" }
    }
  }
  
  override fun pingHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) = stateHolder.onPingMsg(ipAddress, message, nodeId)
  
  override fun ackHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) = stateHolder.nonLobbyOnAck(ipAddress, message, msgT)
  
  
  override fun roleChangeHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    if(!MessageUtils.RoleChangeIdentifier.fromNodeNodeLeave(message)) return
    nodesHolder[ipAddress]?.apply {
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
    nodesHolder[ipAddress]?.apply {
      payload?.handleEvent(inp2p.msg as SteerMsg, inp2p.msgSeq)
    }
  }
  
  fun submitSteerMsg(steerMsg: SteerMsg) {
    player.handleEvent(
      steerMsg, stateHolder.nextSeqNum
    )
  }
  
  override fun cleanup() {
    Logger.info { "$this cleanup" }
    gameEngine.shutdown()
    nodesInitScope.cancel()
  }
  
  override fun toLobby(
    event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
  ) {
    
  }
  
  override fun toPassive(
    changeAccessToken: Any
  ) {
    val (_, depInfo) = stateHolder.masterDeputy!!
    if(depInfo != null) {
      val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
        stateHolder.nextSeqNum,
        senderId = localNode.nodeId,
        receiverId = depInfo.second,
        senderRole = SnakesProto.NodeRole.VIEWER,
        receiverRole = SnakesProto.NodeRole.MASTER,
      )
      nodesHolder[depInfo.first]?.let {
        it.sendToNode(msg)
        it.addMessageForAck(msg)
      }
      PassiveState(
        nodeId = localNode.nodeId,
        gameConfig = internalGameConfig,
        stateHolder = stateHolder,
        clusterNodesHandler = nodesHolder,
      ).apply {
        stateHolder.setupNewState(this, changeAccessToken)
        nodesHolder.filter {
          it.value.nodeId != depInfo.second || it.value.nodeId != localNode.nodeId
        }.forEach { it.value.shutdown() }
        localNode.detach()
      }
    } else {
      toLobby(Event.State.ByController.SwitchToLobby, changeAccessToken)
    }
  }
  
  fun findNewDeputy(
    oldDeputyId: Int?
  ): Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?> {
    val (masterInfo, _) = stateHolder.masterDeputy
      ?: throw IllegalChangeStateAttempt("current master deputy absent")
    
    val deputyCandidate = nodesHolder.find {
      it.value.nodeState == Node.NodeState.Active && it.value.payload != null && it.value.nodeId != oldDeputyId
    }?.value
    
    val newDeputyInfo = deputyCandidate?.let {
      Pair(it.ipAddress, it.nodeId)
    }
    return (masterInfo to newDeputyInfo)
  }
  
  override fun atNodeDetach(
    node: ClusterNodeT<Node.MsgInfo>, changeAccessToken: Any
  ) {
    val (_, depInfo) = stateHolder.masterDeputy!!
    if(node.nodeId != depInfo?.second || node.ipAddress == depInfo.first) return
    val (_, newDep) = findNewDeputy(node.nodeId)
    newDep ?: return
    /**
     * choose new deputy
     */
    val outMsg = MessageUtils.MessageProducer.getRoleChangeMsg(
      msgSeq = stateHolder.nextSeqNum,
      senderId = localNode.nodeId,
      receiverId = newDep.second,
      senderRole = SnakesProto.NodeRole.MASTER,
      receiverRole = SnakesProto.NodeRole.DEPUTY
    )
    stateHolder.sendUnicast(outMsg, newDep.first)
  }
}
