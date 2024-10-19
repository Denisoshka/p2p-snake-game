package d.zhdanov.ccfit.nsu.core.utils

import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType
import d.zhdanov.ccfit.nsu.core.network.utils.AbstractMessageTranslator
import dzhdanov.ccfit.nsu.ru.SnakesProto

object AbstractMessageTranslator :
  AbstractMessageTranslator<SnakesProto.GameMessage> {
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

  override fun fromMessageT(message: SnakesProto.GameMessage): MessageType {
    TODO("Not yet implemented")
  }

  override fun toMessageT(message: MessageType): SnakesProto.GameMessage {
    TODO("Not yet implemented")
  }
}