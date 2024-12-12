package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.connection.Node
import core.network.core.connection.game.ClusterNodeT
import core.network.core.connection.game.impl.ClusterNode
import core.network.core.connection.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.ActiveStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.GameStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(ActiveState::class.java.name)

class ActiveState(
  val nodeId: Int,
  private val stateHolder: NetworkStateHolder,
//  private val controller: NetworkController,
  private val clusterNodesHandler: ClusterNodesHandler,
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
        "incorrect typeCase ${
          message.typeCase
        } has receiverId ${
          message.hasReceiverId()
        } has senderId ${
          message.hasSenderId()
        } "
      }
      return
    }
    
    if(MessageUtils.RoleChangeIdentifier.fromDeputyDeputyMasterNow(message)) {
      /**not handle wait master dead*/
    } else if(MessageUtils.RoleChangeIdentifier.fromMasterPlayerDead(message)) {
      atFromMasterPlayerDead(message)
    } else if(MessageUtils.RoleChangeIdentifier.fromMasterNodeDeputyNow(message)) {
      atFromMasterNodeDeputyNow(message)
    } else if(MessageUtils.RoleChangeIdentifier.fromMasterNodeMasterNow(message)) {
      atFromMasterNodeMasterNow(message)
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
  
  private fun atFromMasterNodeMasterNow(message: SnakesProto.GameMessage) {
  
  }
  
  private fun atFromMasterNodeDeputyNow(message: SnakesProto.GameMessage) {
    val (ms, _) = stateHolder.masterDeputy ?: return
    if(ms.second != message.senderId) return
  }
  
  private fun atFromMasterPlayerDead(message: SnakesProto.GameMessage) {
    val (ms, _) = stateHolder.masterDeputy ?: return
    if(ms.second != message.senderId) return
    
    runBlocking {
      with(stateHolder) {
        switchToLobby(Event.State.ByController.SwitchToLobby)
      }
    }
  }
  
  override fun errorHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
  }
  
  fun submitSteerMsg(steerMsg: SteerMsg) {
    val (masterInfo, _) = stateHolder.masterDeputy ?: return
    clusterNodesHandler[masterInfo.first]?.let {
      val p2pmsg = GameMessage(stateHolder.nextSeqNum, steerMsg)
      val outMsg = MessageTranslator.toGameMessage(p2pmsg, MessageType.SteerMsg)
      it.addMessageForAck(outMsg)
      stateHolder.sendUnicast(outMsg, it.ipAddress)
    }
  }
  
  
  override fun cleanup() {
    clusterNodesHandler.shutdown()
  }
  
  override suspend fun handleNodeDetach(
    node: ClusterNodeT<Node.MsgInfo>
  ) {
    stateHolder.apply {
      val (msInfo, depInfo) = masterDeputy ?: return
      if(depInfo == null) {
        Logger.warn { "activeHandleNodeDetach depInfo absent" }
        reconfigureContext(Event.State.ByController.SwitchToLobby)
        return
      }
      
      if(node.nodeId == msInfo.second && nodeId == depInfo.second && node.ipAddress == msInfo.first) {
        when(val state = latestGameState) {
          null -> stateHolder.apply {
            Logger.warn {
              "during activeDeputyHandleMasterDetach from :${ActiveState::class} to ${MasterState::class} latestGameState is null"
            }
            switchToLobby(Event.State.ByController.SwitchToLobby)
          }
          
          else -> masterNow(state, depInfo)
        }
      } else if(node.nodeId == msInfo.second && nodeId != depInfo.second && node.ipAddress == msInfo.first) {
        normalChangeInfoDeputyToMaster(depInfo, node)
      } else {
        throw IllegalChangeStateAttempt(
          "non master $node try to detach from cluster in state $networkState"
        )
      }
    }
  }
  
  private suspend fun masterNow(
    state: SnakesProto.GameMessage.StateMsg,
    depInfo: Pair<InetSocketAddress, Int>
  ) {
    stateHolder.apply {
      reconfigureMasterDeputy(depInfo to null)
      
      val config = this@ActiveState.gameConfig
      val gamePlayerInfo = GamePlayerInfo(config.playerName, nodeId)
      
      val event = Event.State.ByInternal.MasterNow(
        gameState = state,
        gamePlayerInfo = gamePlayerInfo,
        internalGameConfig = config,
      )
      
      Logger.info {
        "activeDeputyHandleMasterDetach MasterNow config: $config player: $gamePlayerInfo"
      }
      Logger.trace { "switch to master by event $event" }
      
      switchToMaster(stateHolder, event)
    }
  }
  
  private fun normalChangeInfoDeputyToMaster(
    depInfo: Pair<InetSocketAddress, Int>, masterNode: ClusterNode
  ) {
    stateHolder.apply {
      reconfigureMasterDeputy(depInfo to null)
      val unacknowledgedMessages = masterNode.getUnacknowledgedMessages()
      
      val newMasterClusterNode = ClusterNode(
        nodeState = Node.NodeState.Active,
        nodeId = depInfo.second,
        ipAddress = depInfo.first,
        clusterNodesHandler = clusterNodesHandler,
        name = "да и хуй с ним, нам его имя нахуй не нужно"
      )
      
      clusterNodesHandler.registerNode(newMasterClusterNode)
      newMasterClusterNode.apply {
        unacknowledgedMessages.forEach { newMasterClusterNode.sendToNode(it.req) }
        addAllMessageForAck(unacknowledgedMessages)
      }
    }
  }
}