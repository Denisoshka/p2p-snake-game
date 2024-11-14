package d.zhdanov.ccfit.nsu.core.network.nethandlers

import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT

interface NetworkHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, PayloadT : NodePayloadT> :
  AutoCloseable {
  fun launch()
}