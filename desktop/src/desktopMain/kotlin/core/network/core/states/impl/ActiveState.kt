package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.connection.lobby.impl.NetNodeHandler
import core.network.core.states.utils.MasterStateUtils
import core.network.core.states.utils.Utils
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.GamePlayerInfo
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

private val Logger = KotlinLogging.logger(ActiveState::class.java.name)

class ActiveState(
  private val localNode: LocalNode,
  private val stateHolder: StateHolder,
  private val gameConfig: InternalGameConfig,
) : NodeState.ActiveStateT, GameActor {
  private val netNodesHandler: NetNodeHandler = stateHolder.netNodesHandler
  private val nodesHolder: ClusterNodesHandler = stateHolder.nodesHolder
  private val gameController: GameController = stateHolder.gameController
  
  override fun roleChangeHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
  ) {
    if(!MessageUtils.RoleChangeIdentifier.correctRoleChangeMsg(message)) {
      Logger.debug {
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
    Utils.onStateMsg(stateHolder, ipAddress, message)?.let {
      val ack = MessageUtils.MessageProducer.getAckMsg(
        msgSeq = message.msgSeq,
        senderId = it.second,
        receiverId = localNode.nodeId
      )
      nodesHolder[ipAddress]?.sendToNode(ack)
    }
  }
  
  override fun joinHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    /**not handle*/
  }
  
  override fun pingHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    Utils.nonLobbyPingMsg(
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
  
  /*fun normalChangeDeputyToMaster(
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
        clusterNodesHolder = nodesHolder,
      )
      */
  /**
   * да и хуй с ним, нам его имя нахуй не нужно
   * *//*
      nodesHolder.registerNode(newMasterClusterNode)
      newMasterClusterNode.apply {
        unacknowledgedMessages.forEach { newMasterClusterNode.sendToNode(it.req) }
        addAllMessageForAck(unacknowledgedMessages)
      }
    }
  }*/
  
  override fun toMaster(
    gameState: SnakesProto.GameState, accessToken: Any
  ) {
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
  
  /*private fun toLobby(
    event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
  ) {
    nodesHolder.shutdown()
    LobbyState(
      stateHolder = stateHolder,
      netNodesHandler = netNodesHandler,
      gameController = gameController,
    ).apply { stateHolder.setupNewState(this, changeAccessToken) }
    gameController.openLobby()
  }*/
  
  override fun toPassive(
    changeAccessToken: Any
  ): NodeState {
    val (ms, dp) = stateHolder.masterDeputy!!/*вообще такого не должно происходить)*/
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
          receiverRole = SnakesProto.NodeRole.MASTER,
        )
        it.sendToNode(msg)
        it.addMessageForAck(msg)
      }
      return toPassive(accessToken)
    } else if(node.nodeId == msInfo.second) {
      if(dpInfo != null && dpInfo.second == localNode.nodeId) {
        val state = stateHolder.gameState
        if(state != null) {
          return toMaster(state, accessToken)
        } else {
          return toLobby(Event.State.ByController.SwitchToLobby, accessToken)
        }
      } else if(dpInfo != null) {
        return toLobby(Event.State.ByController.SwitchToLobby, accessToken)
      }
    }
    return null
  }
}