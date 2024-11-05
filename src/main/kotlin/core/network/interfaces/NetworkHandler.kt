package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.network.NodesHolder
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT

interface NetworkHandler<
    MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>
    > : AutoCloseable {
  /**
   * require to call [configure] before using [launch]
   * */
  fun configure(context: NodesHolder<MessageT, InboundMessageTranslator>)
  fun launch()
}