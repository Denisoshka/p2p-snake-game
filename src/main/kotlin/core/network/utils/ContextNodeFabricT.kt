package d.zhdanov.ccfit.nsu.core.network.utils

import d.zhdanov.ccfit.nsu.core.interaction.messages.v1.NodeRole
import core.network.nodes.Node
import d.zhdanov.ccfit.nsu.core.network.controller.NetworkController
import java.net.InetSocketAddress
import kotlin.coroutines.CoroutineContext

interface ContextNodeFabricT<
    MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>
    > {
  fun create(
    nodeId: InetSocketAddress,
    initMsgSeqNum: Long,
    nodeRole: NodeRole,
    pingDelay: Long,
    resendDelay: Long,
    thresholdDelay: Long,
    context: NetworkController<MessageT, InboundMessageTranslator>,
    messageComparator: Comparator<MessageT>,
    nodeStateCheckerContext: CoroutineContext
  ): Node<MessageT, InboundMessageTranslator>
}