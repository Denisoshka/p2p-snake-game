package d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.messages.v1.Direction
import d.zhdanov.ccfit.nsu.core.interaction.messages.v1.MessageType

class SteerMsg(val direction: Direction) : d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.Msg(MessageType.SteerMsg)
