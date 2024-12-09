package d.zhdanov.ccfit.nsu.core.utils

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole.DEPUTY
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole.MASTER
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole.NORMAL
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole.VIEWER
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
    
    fun correctCoin(message: SnakesProto.GameMessage): Boolean {
      return message.run {
        hasJoin() && join.run {
          playerName.isNotBlank() && playerType == SnakesProto.PlayerType.HUMAN && (requestedRole == SnakesProto.NodeRole.VIEWER || requestedRole == SnakesProto.NodeRole.NORMAL)
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