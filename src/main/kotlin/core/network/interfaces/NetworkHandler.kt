package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.network.controller.NetworkController
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT

interface NetworkHandler<
    MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>
    > : AutoCloseable {
  /**
   * require to call [configure] before using [launch]
   * */
  fun configure(context: NetworkController<MessageT, InboundMessageTranslator>)
  fun launch()
}