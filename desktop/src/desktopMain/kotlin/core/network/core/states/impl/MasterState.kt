package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.GameContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.LocalNode
import d.zhdanov.ccfit.nsu.core.network.core.states.abstr.GameActor
import d.zhdanov.ccfit.nsu.core.network.core.states.abstr.NodeState
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(MasterState::class.java.name)
private const val JoinInUpdateQ = 10

class MasterState(
  val nodesHolder: ClusterNodesHandler,
  val stateHolder: StateHolder,
  val localNode: LocalNode,
  val gameEngine: GameContext,
  val gameController: GameController,
  val internalGameConfig: InternalGameConfig,
) : NodeState.MasterStateT, GameActor {
  
  init {
    Logger.info { "$this init" }
    gameEngine.launch()
  }
  
  override fun joinHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage
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
        clusterNodesHolder = nodesHolder,
        name = joinMsg.playerName,
      ).apply { nodesHolder.registerNode(this) }
    } catch(e: Exception) {
      Logger.error(e) { "during node registration" }
    }
  }
  
  override fun pingHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) = stateHolder.onPingMsg(ipAddress, message, nodeId)
  
  override fun ackHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) = stateHolder.nonLobbyOnAck(ipAddress, message, msgT)
  
  
  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
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
  
  override fun steerHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage
  ) {
    TODO("Not yet implemented")
  }
  
  override fun errorHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage,
  ) {
  }
  
  override fun announcementHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    TODO("Not yet implemented")
  }
  
  override fun stateHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    TODO("Not yet implemented")
  }
  
  fun submitSteerMsg(steerMsg: SteerMsg) {
    player.handleEvent(
      steerMsg, stateHolder.nextSeqNum
    )
  }
  
  
  override fun toLobby(
    event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
  ): NodeState {
    gameEngine.shutdown()
    nodesHolder.shutdown()
    return LobbyState(
      stateHolder
    
    )
  }
  
  override fun toPassive(
    changeAccessToken: Any
  ): NodeState {
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
  
  override fun atNodeDetachPostProcess(
    node: ClusterNodeT<Node.MsgInfo>,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>?,
    accessToken: Any
  ): NodeState? {
    if(node.nodeId == localNode.nodeId) {
      /**
       * Ну вообще у нас может отвалиться либо наша нода, когда мы становимся
       * вивером, тогда мы должны просто сказать что мы отваливаемся,
       * после перейдем в пассивный режим и будем ждать пока нам начнет
       * присылать сообщения депути(если и он отъебнул в этот момент то нас
       * это не воднует, в протоколе не описани что делать), если он не
       * пришлет нам нихуя, то мы из пассив уйдем в лобби
       **/
      if(dpInfo != null) {
        nodesHolder[dpInfo.first]?.let {
          val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
            msgSeq = stateHolder.nextSeqNum,
            senderId = localNode.nodeId,
            receiverId = dpInfo.second,
            senderRole = SnakesProto.NodeRole.VIEWER,
            receiverRole = SnakesProto.NodeRole.MASTER,
          )
          it.sendToNode(msg)
          it.addMessageForAck(msg)
        }
        return toPassive(accessToken)
      } else {
        return toLobby(Event.State.ByController.SwitchToLobby, accessToken)
      }
    } else if(node.nodeId != localNode.nodeId) {
      /**
       * Либо отъебнули не мы и тогда все ок, просто говорим что чел умер
       **/
      val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
        msgSeq = stateHolder.nextSeqNum,
        senderId = localNode.nodeId,
        receiverId = node.nodeId,
        senderRole = SnakesProto.NodeRole.MASTER,
        receiverRole = SnakesProto.NodeRole.VIEWER
      )
      node.sendToNode(msg)
      node.addMessageForAck(msg)
    }
    return null
  }
}
