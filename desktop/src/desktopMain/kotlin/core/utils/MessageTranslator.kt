package d.zhdanov.ccfit.nsu.core.utils

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameMessage

object MessageTranslator {
  fun getMessageType(message: SnakesProto.GameMessage): MessageType {
    return when(message.typeCase) {
      SnakesProto.GameMessage.TypeCase.PING         -> MessageType.PingMsg
      SnakesProto.GameMessage.TypeCase.STEER        -> MessageType.SteerMsg
      SnakesProto.GameMessage.TypeCase.ACK          -> MessageType.AckMsg
      SnakesProto.GameMessage.TypeCase.STATE        -> MessageType.StateMsg
      SnakesProto.GameMessage.TypeCase.ANNOUNCEMENT -> MessageType.AnnouncementMsg
      SnakesProto.GameMessage.TypeCase.JOIN         -> MessageType.JoinMsg
      SnakesProto.GameMessage.TypeCase.ERROR        -> MessageType.ErrorMsg
      SnakesProto.GameMessage.TypeCase.ROLE_CHANGE  -> MessageType.RoleChangeMsg
      SnakesProto.GameMessage.TypeCase.DISCOVER     -> MessageType.DiscoverMsg
      else                                          -> MessageType.UnrecognisedMsg
    }
  }

  fun fromProto(msg: SnakesProto.GameMessage): GameMessage {
    TODO("Not yet implemented")
  }

  fun fromProto(
    message: SnakesProto.GameMessage, msgT: MessageType
  ): GameMessage {
    TODO("Not yet implemented")
  }

  fun toGameMessage(msg: GameMessage): SnakesProto.GameMessage {
    TODO("Not yet implemented")
  }

  fun toGameMessage(
    msg: GameMessage, msgT: MessageType
  ): SnakesProto.GameMessage {
    TODO("Not yet implemented")
  }
}