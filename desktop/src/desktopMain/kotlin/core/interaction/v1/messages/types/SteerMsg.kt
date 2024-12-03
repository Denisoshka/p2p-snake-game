package d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType

class SteerMsg(val direction: Direction) : Msg(MessageType.SteerMsg)
