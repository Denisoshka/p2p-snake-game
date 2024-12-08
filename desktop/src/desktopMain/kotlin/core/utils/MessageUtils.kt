package d.zhdanov.ccfit.nsu.core.utils

import d.zhdanov.ccfit.nsu.SnakesProto

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
      message: SnakesProto.GameMessage, errorMsg: String?
    ): SnakesProto.GameMessage {
      return SnakesProto.GameMessage.newBuilder().setMsgSeq(message.msgSeq)
        .setError(
          SnakesProto.GameMessage.ErrorMsg.newBuilder()
            .setErrorMessage(errorMsg)
            .build()
        ).build()
    }

    fun getPingMsg(seq: Long): SnakesProto.GameMessage {
      return SnakesProto.GameMessage.newBuilder().setMsgSeq(seq)
        .setPing(pingMsg)
        .build()
    }

    fun getAckMsg(
      msgSeq: Long, senderId: Int, receiverId: Int
    ): SnakesProto.GameMessage {
      return SnakesProto.GameMessage.newBuilder().setMsgSeq(msgSeq)
        .setSenderId(senderId).setReceiverId(receiverId).setAck(
          ackMsg
        ).build()
    }

    fun getAckMsg(
      msgSeq: SnakesProto.GameMessage, senderId: Int, receiverId: Int
    ): SnakesProto.GameMessage {
      return getAckMsg(msgSeq.msgSeq, senderId, receiverId)
    }
  }

  object RoleChangeIdentifier {
    fun correctRoleChangeMsg(msg: SnakesProto.GameMessage) : Boolean {
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