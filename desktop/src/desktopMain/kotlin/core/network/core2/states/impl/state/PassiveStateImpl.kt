package d.zhdanov.ccfit.nsu.core.network.core2.states.impl.state

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.network.core2.states.ConnectedActor
import d.zhdanov.ccfit.nsu.core.network.core2.states.Event
import d.zhdanov.ccfit.nsu.core.network.core2.states.NodeState
import d.zhdanov.ccfit.nsu.core.network.core2.states.StateHolder
import d.zhdanov.ccfit.nsu.core.network.core2.utils.Utils
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger { PassiveStateImpl::class.java }

class PassiveStateImpl(
  private val localNode: ClusterNode,
  private val gameConfig: InternalGameConfig,
  private val stateHolder: StateHolder,
) : NodeState.PassiveState, ConnectedActor {
  private val nodesHolder: ClusterNodesHolder = stateHolder.nodesHolder
  private val gameController: GameController = stateHolder.gameController
  
  override fun joinHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    /**
     * not handle
     */
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
    Utils.nonLobbyOnAck(
      nodesHolder = nodesHolder, ipAddress = ipAddress, message = message
    )
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
  
  override fun submitSteerMsg(steerMsg: SnakesProto.GameMessage.SteerMsg) {
    /**not handle*/
  }
  
  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
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
    if(ack != null) {
      Logger.trace {
        "apply ${message.typeCase} receiverRole : ${message.roleChange.receiverRole} senderRole : ${message.roleChange.senderRole}"
      }
      nodesHolder.sendUnicast(ack, ipAddress)
    } else {
      Logger.trace {
        "receive incorrect ${message.typeCase} receiverRole : ${message.roleChange.receiverRole} senderRole : ${message.roleChange.senderRole}"
      }
    }
  }
  
  override fun announcementHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    /**not handle*/
  }
  
  override fun errorHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    /**not handle*/
  }
  
  override fun steerHandle(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    /**not handle*/
  }
  
  override fun toLobby(
    event: Event.State.ByController.SwitchToLobby
  ): NodeState {
    nodesHolder.shutdown()
    gameController.openLobby()
    return LobbyStateImpl(stateHolder = stateHolder)
  }
  
  override fun atNodeDetachPostProcess(
    node: ClusterNode,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>?
  ): NodeState? {
    /**
     * Я хз просто лишняя перестраховка, чтобы не реагировать на рандомные ноды
     * */
    return if(node.nodeId == msInfo.second && dpInfo == null) {
      toLobby(Event.State.ByController.SwitchToLobby)
    } else {
      null
    }
  }
}