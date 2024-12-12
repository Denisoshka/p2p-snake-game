package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.connection.Node
import core.network.core.connection.game.ClusterNodeT
import core.network.core.connection.game.impl.ClusterNode
import core.network.core.connection.game.impl.ClusterNodesHandler
import core.network.core.connection.game.impl.LocalNode
import core.network.core.states.utils.StateUtils
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.states.ActiveStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.GameStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(ActiveState::class.java.name)

class ActiveState(
  val localNode: LocalNode,
  private val stateHolder: NetworkStateHolder,
  private val nodesHolder: ClusterNodesHandler,
  override val gameConfig: InternalGameConfig,
) : ActiveStateT, GameStateT {
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
  
  
  override fun stateHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) = stateHolder.onStateMsg(ipAddress, message)
  
  override fun roleChangeHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    if(!MessageUtils.RoleChangeIdentifier.correctRoleChangeMsg(message)) {
      Logger.debug {
        "incorrect typeCase ${message.typeCase} has receiverId ${message.hasReceiverId()} has senderId ${message.hasSenderId()} "
      }
      return
    }
    
    if(MessageUtils.RoleChangeIdentifier.fromDeputyDeputyMasterNow(message)) {
      StateUtils.atfromDeputyDeputyMasterNow(message)
    } else if(MessageUtils.RoleChangeIdentifier.fromMasterPlayerDead(message)) {
      StateUtils.atFromMasterPlayerDead(
        localNode, nodesHolder, stateHolder, message, ipAddress,
      )
    } else if(MessageUtils.RoleChangeIdentifier.fromMasterNodeDeputyNow(message)) {
      StateUtils.atFromMasterNodeDeputyNow(
        localNode, nodesHolder, stateHolder, message, ipAddress,
      )
    } else if(MessageUtils.RoleChangeIdentifier.fromMasterNodeMasterNow(message)) {
      StateUtils.atFromMasterNodeMasterNow(
        localNode, nodesHolder, stateHolder, message, ipAddress,
      )
    } else {
      Logger.debug {
        "irrelevant ${
          message.typeCase
        } receiverRole : ${
          message.roleChange.receiverRole
        } senderRole : ${
          message.roleChange.senderRole
        }"
      }
    }
  }
  
  
  override fun errorHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
  }
  
  override fun cleanup() {
  }
  
  override fun handleNodeDetach(
    node: ClusterNodeT<Node.MsgInfo>, changeAccessToken: Any
  ) {
    val (msInfo, depInfo) = stateHolder.masterDeputy ?: return
    if(depInfo == null) {
      Logger.warn { "activeHandleNodeDetach depInfo absent" }
      stateHolder.switchToLobby(
        Event.State.ByController.SwitchToLobby, changeAccessToken
      )
    } else if(node.nodeId == msInfo.second && localNode.nodeId == depInfo.second && node.ipAddress == msInfo.first) {
      val state = stateHolder.latestGameState
      if(state == null) {
        stateHolder.apply {
          Logger.warn {
            "during activeDeputyHandleMasterDetach from :${ActiveState::class} to ${MasterState::class} latestGameState is null"
          }
          switchToLobby(
            Event.State.ByController.SwitchToLobby, changeAccessToken
          )
        }
      } else {
        masterNow(state, depInfo, changeAccessToken)
      }
    } else if(node.nodeId == msInfo.second && localNode.nodeId != depInfo.second && node.ipAddress == msInfo.first) {
      normalChangeDeputyToMaster(depInfo, node, changeAccessToken)
    }
  }
  
  fun masterNow(
    state: SnakesProto.GameMessage.StateMsg,
    depInfo: Pair<InetSocketAddress, Int>,
    changeStateAccessToken: Any
  ) {
    stateHolder.apply {
      reconfigureMasterDeputy(depInfo to null, changeStateAccessToken)
      
      val config = this@ActiveState.gameConfig
      val gamePlayerInfo = GamePlayerInfo(config.playerName, localNode.nodeId)
      
      val event = Event.State.ByInternal.MasterNow(
        gameState = state,
        gamePlayerInfo = gamePlayerInfo,
        internalGameConfig = config,
      )
      
      Logger.info {
        "activeDeputyHandleMasterDetach MasterNow config: $config player: $gamePlayerInfo"
      }
      Logger.trace { "switch to master by event $event" }
      
      switchToMaster(stateHolder, event, changeStateAccessToken)
    }
  }
  
  fun deputyNow(
    state: SnakesProto.GameMessage.StateMsg,
    depInfo: Pair<InetSocketAddress, Int>,
    changeStateAccessToken: Any
  ) {
    stateHolder.apply {
    
    }
  }
  
  fun normalChangeDeputyToMaster(
    depInfo: Pair<InetSocketAddress, Int>,
    masterNode: ClusterNodeT<Node.MsgInfo>,
    changeStateAccessToken: Any
  ) {
    stateHolder.apply {
      reconfigureMasterDeputy(depInfo to null, changeStateAccessToken)
      val unacknowledgedMessages = masterNode.getUnacknowledgedMessages()
      
      val newMasterClusterNode = ClusterNode(
        nodeState = Node.NodeState.Passive,
        nodeId = depInfo.second,
        ipAddress = depInfo.first,
        clusterNodesHandler = nodesHolder,
        name = "да и хуй с ним, нам его имя нахуй не нужно"
      )
      
      nodesHolder.registerNode(newMasterClusterNode)
      newMasterClusterNode.apply {
        unacknowledgedMessages.forEach { newMasterClusterNode.sendToNode(it.req) }
        addAllMessageForAck(unacknowledgedMessages)
      }
    }
  }
}