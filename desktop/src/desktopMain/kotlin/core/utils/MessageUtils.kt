package d.zhdanov.ccfit.nsu.core.utils

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole.DEPUTY
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole.MASTER
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole.NORMAL
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole.VIEWER
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.exceptions.IllegalNodeRoleException

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
      senderRole: SnakesProto.NodeRole? = null,
      receiverRole: SnakesProto.NodeRole? = null,
    ): SnakesProto.GameMessage {
      val roleCng = SnakesProto.GameMessage.RoleChangeMsg.newBuilder().apply {
        senderRole?.let { setSenderRole(it) }
        receiverRole?.let { setReceiverRole(it) }
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
      nodeRole: SnakesProto.NodeRole
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
    
    @Throws(IllegalNodeRoleException::class)
    fun nodeRolefromProto(role: SnakesProto.NodeRole): NodeRole {
      return when(role) {
        SnakesProto.NodeRole.NORMAL -> NORMAL
        SnakesProto.NodeRole.MASTER -> MASTER
        SnakesProto.NodeRole.DEPUTY -> DEPUTY
        SnakesProto.NodeRole.VIEWER -> VIEWER
        else                        -> throw IllegalNodeRoleException(role.toString())
      }
    }
    
    fun nodeRoleToProto(
      role: NodeRole
    ): SnakesProto.NodeRole {
      return when(role) {
        VIEWER -> SnakesProto.NodeRole.VIEWER
        NORMAL -> SnakesProto.NodeRole.NORMAL
        DEPUTY -> SnakesProto.NodeRole.DEPUTY
        MASTER -> SnakesProto.NodeRole.MASTER
      }
    }
    
    fun DirectionFromProto(dir: SnakesProto.Direction): Direction {
      return when(dir) {
        SnakesProto.Direction.UP    -> Direction.UP
        SnakesProto.Direction.DOWN  -> Direction.DOWN
        SnakesProto.Direction.LEFT  -> Direction.LEFT
        SnakesProto.Direction.RIGHT -> Direction.RIGHT
      }
    }
    
    fun DirectionToProto(dir: Direction): SnakesProto.Direction {
      return when(dir) {
        Direction.UP    -> SnakesProto.Direction.UP
        Direction.DOWN  -> SnakesProto.Direction.DOWN
        Direction.LEFT  -> SnakesProto.Direction.LEFT
        Direction.RIGHT -> SnakesProto.Direction.RIGHT
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