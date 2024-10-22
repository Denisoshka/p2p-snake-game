package d.zhdanov.ccfit.nsu.core.interaction.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.messages.MessageType

class SteerMsg(val direction: Direction) : Msg(MessageType.SteerMsg)
