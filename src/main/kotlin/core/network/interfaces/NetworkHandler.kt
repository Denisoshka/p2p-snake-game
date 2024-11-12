package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.network.core.P2PContext
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT

interface NetworkHandler<
    MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>
    > : AutoCloseable {
  /**
   * require to call [configure] before using [launch]
   * */
  fun configure(context: P2PContext<MessageT, InboundMessageTranslator>)
  fun launch()
}