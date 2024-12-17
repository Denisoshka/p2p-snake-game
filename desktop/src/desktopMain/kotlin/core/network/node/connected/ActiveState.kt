package d.zhdanov.ccfit.nsu.core.network.node.connected

import core.network.core.states.utils.MasterStateUtils
import core.network.core.states.utils.Utils
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.LocalNode
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.Logger
import d.zhdanov.ccfit.nsu.core.network.states.abstr.ConnectedActor
import d.zhdanov.ccfit.nsu.core.network.states.abstr.NodeState
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import java.net.InetSocketAddress

class ActiveState(
  private val localNode: LocalNode,
  private val stateHolder: StateHolder,
  private val gameConfig: InternalGameConfig,
) : NodeState.ActiveStateT, ConnectedActor {
  private val nodesHolder: ClusterNodesHolder = stateHolder.nodesHolder
  private val gameController: GameController = stateHolder.gameController
  
  override fun submitSteerMsg(steerMsg: SnakesProto.GameMessage.SteerMsg) {
    Utils.onNonMasterSubmitSteer(stateHolder, nodesHolder, steerMsg)
  }
  
  override fun roleChangeHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
  ) {
    if(!MessageUtils.RoleChangeIdentifier.correctRoleChangeMsg(message)) {
      Logger.trace {
        "incorrect typeCase ${message.typeCase} has receiverId ${message.hasReceiverId()} has senderId ${message.hasSenderId()} "
      }
      return
    }
    
    val (ms, dp) = stateHolder.masterDeputy ?: return
    var ack: SnakesProto.GameMessage? = null
    if(MessageUtils.RoleChangeIdentifier.fromDeputyDeputyMasterNow(message)) {
      if(Utils.atFromDeputyDeputyMasterNow(
          ms, dp, nodesHolder, message, ipAddress
        )
      ) {
        ack = MessageUtils.MessageProducer.getAckMsg(
          message.msgSeq, dp!!.second, localNode.nodeId
        )
      }
    }
    if(MessageUtils.RoleChangeIdentifier.fromMasterPlayerDead(message)) {
      if(Utils.atFromMasterPlayerDead(
          ms, localNode, message, ipAddress,
        )
      ) {
        ack = ack ?: MessageUtils.MessageProducer.getAckMsg(
          message.msgSeq, ms.second, localNode.nodeId
        )
      }
    }
    if(MessageUtils.RoleChangeIdentifier.fromMasterNodeDeputyNow(message)) {
      Utils.atFromMasterNodeDeputyNow(
        ms, dp, localNode, stateHolder, message, ipAddress,
      )
      ack = ack ?: MessageUtils.MessageProducer.getAckMsg(
        message.msgSeq, ms.second, localNode.nodeId
      )
    }
    if(MessageUtils.RoleChangeIdentifier.fromMasterNodeMasterNow(message)) {
      Utils.atFromMasterNodeMasterNow(
        ms, dp, localNode, nodesHolder, message, ipAddress,
      )
      ack = ack ?: MessageUtils.MessageProducer.getAckMsg(
        message.msgSeq, ms.second, localNode.nodeId
      )
    }
    
    if(ack != null) {
      Logger.trace {
        "apply ${message.typeCase} receiverRole : ${message.roleChange.receiverRole} senderRole : ${message.roleChange.senderRole}"
      }
      stateHolder.sendUnicast(ack, ipAddress)
    } else {
      Logger.trace {
        "receive incorrect ${message.typeCase} receiverRole : ${message.roleChange.receiverRole} senderRole : ${message.roleChange.senderRole}"
      }
    }
  }
  
  override fun steerHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    /**not handle*/
  }
  
  override fun stateHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    Utils.onStateMsg(
      stateHolder = stateHolder,
      nodesHolder = nodesHolder,
      localNode = localNode,
      ipAddress = ipAddress,
      message = message
    )
  }
  
  override fun joinHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    /**not handle*/
  }
  
  override fun pingHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    Utils.nonLobbyNonMasterOnPingMsg(
      stateHolder = stateHolder,
      nodesHolder = nodesHolder,
      localNode = localNode,
      ipAddress = ipAddress,
      message = message
    )
  }
  
  override fun ackHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    Utils.nonLobbyOnAck(nodesHolder, ipAddress, message)
  }
  
  override fun errorHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    /**not handle*/
  }
  
  override fun announcementHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    /**not handle*/
  }
  
  override fun toMaster(
    gameState: SnakesProto.GameState, accessToken: Any
  ): NodeState {
    val (newMs, newDp) = stateHolder.masterDeputy!!
    newDp?.let {
    
    }
    newDepInfo?.let {
      ClusterNode(
        nodeState = Node.NodeState.Active,
        nodeId = it.second,
        ipAddress = it.first,
        clusterNodesHolder = nodesHolder,
        name = newDep.name
      ).apply {
        nodesHolder.registerNode(this)
        val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
          msgSeq = nextSeqNum,
          senderId = msInfo.second,
          receiverId = newDepInfo.second,
          senderRole = SnakesProto.NodeRole.MASTER,
          receiverRole = SnakesProto.NodeRole.DEPUTY
        )
        sendToNode(msg)
        addMessageForAck(msg)
      }
    }
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
        clusterNodesHolder = nodesHolder,
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
  ): LobbyState {
    nodesHolder.shutdown()
    gameController.openLobby()
    return LobbyState(stateHolder = stateHolder)
  }
  
  override fun toPassive(
    changeAccessToken: Any
  ): NodeState {
    val (ms, dp) = stateHolder.masterDeputy!!
    /**там вообще null не должно происходить)*/
    val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
      stateHolder.nextSeqNum,
      senderId = localNode.nodeId,
      receiverId = ms.second,
      senderRole = NodeRole.VIEWER,
    )
    nodesHolder[ms.first]?.let {
      it.sendToNode(msg)
      it.addMessageForAck(msg)
      /**
       * ну если мастера вдруг не будет то мы обратимся к deputy
       * */
    }
    return PassiveState(
      localNode = localNode,
      gameConfig = gameConfig,
      stateHolder = stateHolder,
    )
  }
  
  override fun atNodeDetachPostProcess(
    node: ClusterNodeT<Node.MsgInfo>,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>?,
    accessToken: Any
  ): NodeState? {
    /**
     * Здесь есть несколько вариантов развития событий,
     * 1) Отъебнули мы, тогда говорим мастеру что мы теперь вивер,
     * 2) Отъебнул мастер, тогда смотрим на депути
     * 2.1) Мы депути
     * 2.1.1) Есть стейт, то мы мастер
     * 2.1.2) Нет стейта, то мы ливаем нахуй
     * 2.2) Если депути не мы то мы остаемся и депути вообще существует то
     * остаемся в том же стейте
     * 2.3) Если депути нет то мы выходим в лобби, ибо че за хуйня
     * */
    if(node.nodeId == localNode.nodeId) {
      /**
       * В этом стейте можем отъебнуть только мы, тогда мы просто говорим
       * мастеру, что мы становимся вивером
       */
      nodesHolder[msInfo.first]?.let {
        val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
          msgSeq = stateHolder.nextSeqNum,
          senderId = localNode.nodeId,
          receiverId = msInfo.second,
          senderRole = SnakesProto.NodeRole.VIEWER,
        )
        it.sendToNode(msg)
        it.addMessageForAck(msg)
      }
      return toPassive(accessToken)
    } else if(node.nodeId == msInfo.second) {
      if(dpInfo != null && dpInfo.second == localNode.nodeId) {
        val state = stateHolder.gameState
        return if(state != null) {
          toMaster(state, accessToken)
        } else {
          toLobby(Event.State.ByController.SwitchToLobby, accessToken)
        }
      } else if(dpInfo != null) {
        return toLobby(Event.State.ByController.SwitchToLobby, accessToken)
      }
    }
    return null
  }
}