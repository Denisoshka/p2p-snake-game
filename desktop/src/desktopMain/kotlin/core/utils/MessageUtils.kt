package d.zhdanov.ccfit.nsu.core.utils

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt

object MessageUtils {
  private val ackMsg: SnakesProto.GameMessage.AckMsg =
    SnakesProto.GameMessage.AckMsg.newBuilder().build()
  private val pingMsg: SnakesProto.GameMessage.PingMsg =
    SnakesProto.GameMessage.PingMsg.newBuilder().build()
  val messageComparator = getComparator()
  
  object Preconditions {
    fun needToAcknowledge(
      msgDescriptor: SnakesProto.GameMessage.TypeCase
    ): Boolean {
      return msgDescriptor == SnakesProto.GameMessage.TypeCase.ANNOUNCEMENT || msgDescriptor == SnakesProto.GameMessage.TypeCase.ACK || msgDescriptor == SnakesProto.GameMessage.TypeCase.DISCOVER
    }
    
    fun checkJoin(message: SnakesProto.GameMessage.JoinMsg) {
      if(message.playerName.isBlank()) {
        throw IllegalNodeRegisterAttempt("player name is blank")
      }
      if(message.playerType != SnakesProto.PlayerType.HUMAN) {
        throw IllegalNodeRegisterAttempt("player type must be human")
      }
      when(val role = message.requestedRole) {
        SnakesProto.NodeRole.NORMAL, SnakesProto.NodeRole.MASTER -> {}
        else                                                     -> {
          throw IllegalNodeRegisterAttempt("illegal role requester $role")
        }
      }
    }
  }
  
  private fun getComparator(): Comparator<SnakesProto.GameMessage> {
    return Comparator { msg1, msg2 ->
      msg1.msgSeq.compareTo(msg2.msgSeq)
    }
  }
  
  object MessageProducer {
    fun getErrorMsg(
      msgSeq: Long, errorMsg: String
    ): SnakesProto.GameMessage {
      val error =
        SnakesProto.GameMessage.ErrorMsg.newBuilder().setErrorMessage(errorMsg)
          .build()
      return SnakesProto.GameMessage.newBuilder().setMsgSeq(msgSeq)
        .setError(error).build()
    }
    
    fun getPingMsg(seq: Long): SnakesProto.GameMessage {
      return SnakesProto.GameMessage.newBuilder().setMsgSeq(seq)
        .setPing(pingMsg).build()
    }
    
    fun getAckMsg(
      msgSeq: Long, senderId: Int, receiverId: Int
    ): SnakesProto.GameMessage {
      return SnakesProto.GameMessage.newBuilder().setMsgSeq(msgSeq)
        .setSenderId(senderId).setReceiverId(receiverId).setAck(
          ackMsg
        ).build()
    }
    
    fun getRoleChangeMsg(
      msgSeq: Long,
      senderId: Int,
      receiverId: Int,
      senderRole: NodeRole? = null,
      receiverRole: NodeRole? = null,
    ): SnakesProto.GameMessage {
      val roleCng = SnakesProto.GameMessage.RoleChangeMsg.newBuilder().apply {
        senderRole?.let { setSenderRole(it.toProto()) }
        receiverRole?.let { setReceiverRole(it.toProto()) }
      }.build()
      
      return SnakesProto.GameMessage.newBuilder().apply {
        setMsgSeq(msgSeq)
        setSenderId(senderId)
        setReceiverId(receiverId)
        setRoleChange(roleCng)
      }.build()
    }
    
    fun getStateMsg(
      msgSeq: Long, state: SnakesProto.GameMessage.StateMsg
    ): SnakesProto.GameMessage {
      return SnakesProto.GameMessage.newBuilder().apply {
        setMsgSeq(msgSeq)
        setState(state)
      }.build()
    }
    
    fun getStateMsg(
      msgSeq: Long, state: SnakesProto.GameState
    ): SnakesProto.GameMessage {
      val stateMsg = SnakesProto.GameMessage.StateMsg.newBuilder().apply {
        setState(state)
      }.build()
      return getStateMsg(msgSeq, stateMsg)
    }
    
    fun getAckMsg(
      msgSeq: SnakesProto.GameMessage, senderId: Int, receiverId: Int
    ): SnakesProto.GameMessage {
      return getAckMsg(msgSeq.msgSeq, senderId, receiverId)
    }
    
    fun getJoinMsg(
      msgSeq: Long,
      playerType: SnakesProto.PlayerType,
      playerName: String,
      gameName: String,
      nodeRole: NodeRole
    ): SnakesProto.GameMessage {
      val join = SnakesProto.GameMessage.JoinMsg.newBuilder().apply {
        setPlayerType(playerType)
        setPlayerName(playerName)
        setGameName(gameName)
        setRequestedRole(nodeRole)
      }.build()
      return SnakesProto.GameMessage.newBuilder().setJoin(join).build()
    }
    
    fun getSteerMsg(
      msgSeq: Long, steerMsg: SnakesProto.GameMessage.SteerMsg
    ): SnakesProto.GameMessage {
      return SnakesProto.GameMessage.newBuilder().apply {
        setSteer(steerMsg)
        setMsgSeq(msgSeq)
      }.build()
    }
    
    fun getSnakeMsgBuilder(
      playerId: Int,
      headWithOffsets: List<SnakesProto.GameState.Coord.Builder>,
      snakeState: SnakeState,
      direction: Direction
    ): SnakesProto.GameState.Snake.Builder {
      return SnakesProto.GameState.Snake.newBuilder().apply {
        setPlayerId(playerId)
        setState(snakeState.toProto())
        pointsBuilderList.addAll(headWithOffsets)
        headDirection = direction.toProto()
      }
    }
    
    fun getCoordBuilder(x: Int, y: Int): SnakesProto.GameState.Coord.Builder {
      return SnakesProto.GameState.Coord.newBuilder().setX(x).setY(y)
    }
    
    fun getCoord(x: Int, y: Int): SnakesProto.GameState.Coord {
      return getCoordBuilder(x, y).build()
    }
    
    fun getSnakeMsg(
      playerId: Int,
      headWithOffsets: List<SnakesProto.GameState.Coord.Builder>,
      snakeState: SnakeState,
      direction: Direction
    ): SnakesProto.GameState.Snake {
      return getSnakeMsgBuilder(
        playerId, headWithOffsets, snakeState, direction
      ).build()
    }
    
    fun getMessageForNewMaster(
      message: SnakesProto.GameMessage, senderId: Int, receiverId: Int
    ): SnakesProto.GameMessage {
      return when(message.typeCase) {
        SnakesProto.GameMessage.TypeCase.ACK, SnakesProto.GameMessage.TypeCase.ROLE_CHANGE -> {
          message.toBuilder().setSenderId(senderId).setReceiverId(receiverId)
            .build()
        }
        
        else                                                                               -> {
          message
        }
      }
    }
  }
  
  object RoleChangeIdentifier {
    fun correctRoleChangeMsg(msg: SnakesProto.GameMessage): Boolean {
      return msg.hasRoleChange() && msg.hasReceiverId() && msg.hasSenderId()
    }
    
    fun fromDeputyDeputyMasterNow(msg: SnakesProto.GameMessage): Boolean {
      val roleChangeMsg = msg.roleChange
      return roleChangeMsg.senderRole == SnakesProto.NodeRole.MASTER && !roleChangeMsg.hasReceiverRole()
    }
    
    fun fromNodeNodeLeave(msg: SnakesProto.GameMessage): Boolean {
      val roleChangeMsg = msg.roleChange
      return roleChangeMsg.senderRole == SnakesProto.NodeRole.VIEWER && !roleChangeMsg.hasReceiverRole()
    }
    
    fun fromMasterPlayerDead(msg: SnakesProto.GameMessage): Boolean {
      val roleChangeMsg = msg.roleChange
      return roleChangeMsg.receiverRole == SnakesProto.NodeRole.VIEWER
    }
    
    fun fromMasterNodeDeputyNow(msg: SnakesProto.GameMessage): Boolean {
      val roleChangeMsg = msg.roleChange
      return roleChangeMsg.receiverRole == SnakesProto.NodeRole.DEPUTY
    }
    
    fun fromMasterNodeMasterNow(msg: SnakesProto.GameMessage): Boolean {
      val roleChangeMsg = msg.roleChange
      return roleChangeMsg.senderRole == SnakesProto.NodeRole.VIEWER && roleChangeMsg.receiverRole == SnakesProto.NodeRole.MASTER
    }
  }
}