package d.zhdanov.ccfit.nsu.core.network.exceptions

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType

class IllegalGameMessageType(required: MessageType, provided: MessageType) :
  RuntimeException("require type $required, provided message type $provided")