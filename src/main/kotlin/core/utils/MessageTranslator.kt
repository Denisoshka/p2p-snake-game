package d.zhdanov.ccfit.nsu.core.utils

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT

object MessageTranslator : MessageTranslatorT<SnakesProto.GameMessage> {
  override fun getMessageType(message: SnakesProto.GameMessage): MessageType {
    return when (message.typeCase) {
      SnakesProto.GameMessage.TypeCase.PING -> MessageType.PingMsg
      SnakesProto.GameMessage.TypeCase.STEER -> MessageType.SteerMsg
      SnakesProto.GameMessage.TypeCase.ACK -> MessageType.AckMsg
      SnakesProto.GameMessage.TypeCase.STATE -> MessageType.StateMsg
      SnakesProto.GameMessage.TypeCase.ANNOUNCEMENT -> MessageType.AnnouncementMsg
      SnakesProto.GameMessage.TypeCase.JOIN -> MessageType.JoinMsg
      SnakesProto.GameMessage.TypeCase.ERROR -> MessageType.ErrorMsg
      SnakesProto.GameMessage.TypeCase.ROLE_CHANGE -> MessageType.RoleChangeMsg
      SnakesProto.GameMessage.TypeCase.DISCOVER -> MessageType.DiscoverMsg
      else -> MessageType.UnrecognisedMsg
    }
  }

  override fun fromMessageT(msg: SnakesProto.GameMessage): P2PMessage {
    TODO("Not yet implemented")
  }

  override fun fromMessageT(
    message: SnakesProto.GameMessage, msgT: MessageType
  ): P2PMessage {
    TODO("Not yet implemented")
  }

  override fun toMessageT(msg: P2PMessage): SnakesProto.GameMessage {
    TODO("Not yet implemented")
  }

  override fun toMessageT(
    msg: P2PMessage, msgT: MessageType
  ): SnakesProto.GameMessage {
    TODO("Not yet implemented")
  }
}