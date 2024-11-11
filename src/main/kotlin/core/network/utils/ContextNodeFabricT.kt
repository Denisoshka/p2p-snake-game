package d.zhdanov.ccfit.nsu.core.network.utils

import d.zhdanov.ccfit.nsu.core.interaction.messages.v1.NodeRole
import d.zhdanov.ccfit.nsu.core.network.states.Node
import d.zhdanov.ccfit.nsu.core.network.states.P2PContext
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
    context: P2PContext<MessageT, InboundMessageTranslator>,
    messageComparator: Comparator<MessageT>,
    nodeStateCheckerContext: CoroutineContext
  ): Node<MessageT, InboundMessageTranslator>
}