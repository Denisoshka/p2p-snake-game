package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.connection.Node
import core.network.core.connection.game.ClusterNodeT
import core.network.core.connection.game.impl.ClusterNode
import core.network.core.connection.game.impl.ClusterNodesHandler
import core.network.core.connection.game.impl.LocalNode
import core.network.core.connection.lobby.impl.NetNodeHandler
import core.network.core.states.utils.MasterStateUtils
import core.network.core.states.utils.StateUtils
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.states.ActiveStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.GameStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.Switches
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(ActiveState::class.java.name)

class ActiveState(
  val localNode: LocalNode,
  private val nodesHolder: ClusterNodesHandler,
  private val gameController: GameController,
  private val netNodesHandler: NetNodeHandler,
  private val stateHolder: NetworkStateHolder,
  override val internalGameConfig: InternalGameConfig,
) : ActiveStateT, GameStateT, Switches.FromActive {
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
    val (msInfo, depInfo) = stateHolder.masterDeputy!!
    if(depInfo == null) {
      Logger.warn { "activeHandleNodeDetach depInfo absent" }
      this.toLobby(Event.State.ByController.SwitchToLobby, changeAccessToken)
    } else if(node.nodeId == msInfo.second && localNode.nodeId == depInfo.second && node.ipAddress == msInfo.first) {
      val state = stateHolder.latestGameState
      if(state == null) {
        Logger.warn {
          "during activeDeputyHandleMasterDetach from :${ActiveState::class} to ${MasterState::class} latestGameState is null"
        }
        this.toLobby(
          Event.State.ByController.SwitchToLobby, changeAccessToken
        )
      } else {
        this.toMaster(
          gameState = state,
          accessToken = changeAccessToken
        )
      }
    } else if(node.nodeId == msInfo.second && localNode.nodeId != depInfo.second && node.ipAddress == msInfo.first) {
    
//      normalChangeDeputyToMaster(depInfo, node, changeAccessToken)
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
        name = ""
      )
      /**
       * да и хуй с ним, нам его имя нахуй не нужно
       * */
      nodesHolder.registerNode(newMasterClusterNode)
      newMasterClusterNode.apply {
        unacknowledgedMessages.forEach { newMasterClusterNode.sendToNode(it.req) }
        addAllMessageForAck(unacknowledgedMessages)
      }
    }
  }
  
  override fun toMaster(
    accessToken: Any,
    gameState: SnakesProto.GameMessage.StateMsg
  ) {
    val newMs = localNode.ipAddress to localNode.nodeId
    stateHolder.reconfigureMasterDeputy(newMs to null, accessToken)
    val gamePlayerInfo = GamePlayerInfo(
      this@ActiveState.internalGameConfig.playerName, localNode.nodeId
    )
    Logger.info {
      "activeDeputyHandleMasterDetach MasterNow config: ${this@ActiveState.internalGameConfig} player: $gamePlayerInfo"
    }
    
    try {
      MasterStateUtils.prepareMasterFromState(
        state = gameState,
        clusterNodesHandler = nodesHolder,
        gameConfig = internalGameConfig,
        gamePlayerInfo = gamePlayerInfo,
        stateHolder = stateHolder
      ).apply {
        stateHolder.setupNewState(this, accessToken)
        val msDp = this@apply.findNewDeputy(null)
        assert(msDp.first.second == localNode.nodeId)
        stateHolder.reconfigureMasterDeputy(msDp, accessToken)
        
        msDp.second?.first?.let {
          nodesHolder[it]?.apply {
            val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
              stateHolder.nextSeqNum,
              senderId = msDp.first.second,
              receiverId = this.nodeId,
              receiverRole = SnakesProto.NodeRole.DEPUTY
            )
            sendToNode(msg)
            addMessageForAck(msg)
          }
        }
        
        for(node in nodesHolder) {
          node.value.apply {
            val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
              stateHolder.nextSeqNum,
              senderId = msDp.first.second,
              receiverId = nodeId,
              senderRole = SnakesProto.NodeRole.MASTER,
            )
            sendToNode(msg)
            addMessageForAck(msg)
          }
        }
      }
    } catch(e: Exception) {
      Logger.error(e) { "during switchToMaster" }
      throw e
    }
  }
  
  override fun toLobby(
    event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
  ) {
    nodesHolder.shutdown()
    LobbyState(
      stateHolder = stateHolder,
      netNodesHandler = netNodesHandler,
      gameController = gameController,
    ).apply { stateHolder.setupNewState(this, changeAccessToken) }
    gameController.openLobby()
  }
  
  override fun toPassive(
    changeAccessToken: Any
  ) {
    val (ms, dp) = stateHolder.masterDeputy!!
    /*вообще такого не должно происходить)*/
    val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
      stateHolder.nextSeqNum,
      senderId = localNode.nodeId,
      receiverId = ms.second,
      senderRole = SnakesProto.NodeRole.VIEWER,
      receiverRole = null,
    )
    nodesHolder[ms.first]?.let {
      it.sendToNode(msg)
      it.addMessageForAck(msg)
      /**
       * ну если мастера вдруг не будет то мы обратимся к deputy
       * */
    }
    PassiveState(
      nodeId = localNode.nodeId,
      gameConfig = internalGameConfig,
      stateHolder = stateHolder,
      clusterNodesHandler = nodesHolder,
    ).apply {
      stateHolder.setupNewState(this, changeAccessToken)
    }
  }
}