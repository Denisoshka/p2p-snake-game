package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.network.states.Node
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT

interface NodeContext<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>> {
	suspend fun handleNodeRegistration(node: Node<MessageT, InboundMessageTranslator>)
	suspend fun handleNodeTermination(node: Node<MessageT, InboundMessageTranslator>)
	suspend fun handleNodeRoleChange(
    node: Node<MessageT, InboundMessageTranslator>, p2pRoleChange: P2PMessage
	)
}