package d.zhdanov.ccfit.nsu.core.messages.types

import d.zhdanov.ccfit.nsu.core.messages.GameMessage
import d.zhdanov.ccfit.nsu.core.messages.MessageType

class ErrorMsg(val message: String) : GameMessage(0, MessageType.ErrorMsg) {
}
