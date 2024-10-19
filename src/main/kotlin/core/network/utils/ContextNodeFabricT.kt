package d.zhdanov.ccfit.nsu.core.network.utils

import d.zhdanov.ccfit.nsu.core.interaction.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.Node
import d.zhdanov.ccfit.nsu.core.network.P2PContext
import java.net.InetSocketAddress
import kotlin.coroutines.CoroutineContext

interface ContextNodeFabricT<
    MessageT, InboundMessageTranslator : AbstractMessageTranslator<MessageT>
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