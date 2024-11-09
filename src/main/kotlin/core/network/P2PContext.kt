package d.zhdanov.ccfit.nsu.core.network

import core.network.nethandlers.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.network.nethandlers.MulticastNetHandler
import d.zhdanov.ccfit.nsu.core.network.utils.ContextNodeFabricT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageUtilsT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

class P2PContext<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
	contextJoinBacklog: Int,
	val messageUtils: MessageUtilsT<MessageT, MessageType>,
	val pingDelay: Long,
	val resendDelay: Long,
	val thresholdDelay: Long,
	private val unicastNetHandler: UnicastNetHandler<MessageT, MessageType, InboundMessageTranslator>,
	private val multicastNetHandler: MulticastNetHandler<MessageT, MessageType, InboundMessageTranslator>,
	private val messageTranslator: InboundMessageTranslator,
	private val contextNodeFabric: ContextNodeFabricT<MessageT, InboundMessageTranslator>
) : AutoCloseable {
	init {
		unicastNetHandler.configure(this)
		multicastNetHandler.configure(this)
	}

	private val nodesIdSupplier = AtomicLong(0)
	private val joinQueueIsFull =
		"The join queue is full. Please try again later."
	private val joinNotForMaster = "Join request allowed for master node."

	private val commandHandler = CommandHandler(this)
	private val messageComparator = messageUtils.getComparator()


	fun initContext(master: InetSocketAddress?) {

	}

	fun destroyContext(
	) {
	}

	override fun close() {
		unicastNetHandler.close()
		multicastNetHandler.close()
	}

	fun handleUnicastMessage(
		message: MessageT, inetSocketAddress: InetSocketAddress
	) {
		when (val msgT = messageTranslator.getMessageType(message)) {
			MessageType.PingMsg -> commandHandler.pingHandle(
				inetSocketAddress, message
			)

			MessageType.AckMsg -> commandHandler.ackHandle(
				inetSocketAddress, message
			)

			MessageType.StateMsg -> commandHandler.stateHandle(
				inetSocketAddress, message, msgT
			)

			MessageType.JoinMsg -> commandHandler.joinHandle(
				inetSocketAddress, message, msgT
			)

			MessageType.SteerMsg -> commandHandler.steerHandle(
				inetSocketAddress, message, msgT
			)

			MessageType.ErrorMsg -> commandHandler.errorHandle(
				inetSocketAddress, message, msgT
			)

			MessageType.RoleChangeMsg -> commandHandler.roleChangeHandle(
				inetSocketAddress, message, msgT
			)

			else -> logger.info {
				"$msgT from $inetSocketAddress not handle as unicast command"
			}
		}
	}

	fun handleMulticastMessage(
		message: MessageT, inetSocketAddress: InetSocketAddress
	) {
		when (val msgT = messageTranslator.getMessageType(message)) {
			MessageType.AnnouncementMsg -> commandHandler.announcementHandle(
				inetSocketAddress, message, msgT
			)

			else -> logger.debug {
				"$msgT from $inetSocketAddress not handle as unicast command"
			}
		}
	}

	/**
	 * send message without add for acknowledgement monitoring
	 * */
	fun sendUnicast(
		msg: MessageT, node: Node<MessageT, InboundMessageTranslator>
	) = unicastNetHandler.sendUnicastMessage(msg, node.address)


	fun clusterTaskStateUpdated(stateMsg: StateMsg) {
		msg = messageTranslator.toMessageT(stateMsg, MessageType.StateMsg)
		for ((_, nodeContext) in nodes) {
			val node = nodeContext.first
			val seg = node.getNextMSGSeqNum()
		}
	}

	private fun checkJoinPreconditions(joinMsg: JoinMsg): Boolean {
		return joinMsg.nodeRole == NodeRole.NORMAL || joinMsg.nodeRole == NodeRole.VIEWER
	}

	private fun retryJoinLater(
		message: MessageT, node: Node<MessageT, InboundMessageTranslator>
	) {

	}

	private fun acknowledgeMessage(
		message: MessageT, node: Node<MessageT, InboundMessageTranslator>
	) {
		val ackMsg = messageUtils.getAckMsg(message, localNode.nodeId, node.nodeId)
		unicastNetHandler.sendUnicastMessage(ackMsg, node.address)
	}


	private fun vsePoshloPoPizde() {

	}


	//  ну вроде функция закончена


	fun sendMessage(
		msg: P2PMessage, node: Node<MessageT, InboundMessageTranslator>
	) {
	}


	private fun changeNodeRole(
		node: Node<MessageT, InboundMessageTranslator>, message: P2PMessage
	) {
		if (message.msg.type != MessageType.RoleChangeMsg) return
		val msg = message.msg as RoleChangeMsg

	}

	class CommandHandler<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
		private val context: P2PContext<MessageT, InboundMessageTranslator>
	) {
		private val translator = context.messageTranslator

		fun joinHandle(
			ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
		) {
			context.run {
				if (messageUtils.checkJoinPreconditions(message)) return
				val pair = nodes[ipAddress]
				if (pair == null) {
					val node = contextNodeFabric.create()
				} else {
					val errMsg = messageUtils.newErrorMsg(message, "node already joined")
					unicastNetHandler.sendUnicastMessage(errMsg, ipAddress)
				}
			}
		}

		fun pingHandle(
			inetSocketAddress: InetSocketAddress, message: MessageT
		) {
			val nodeAndPlayerContext = nodes[inetSocketAddress] ?: return
			val node = nodeAndPlayerContext.first
			node.addMessageForAcknowledge(message)
			context.acknowledgeMessage(message, node)
		}

		fun ackHandle(
			inetSocketAddress: InetSocketAddress, message: MessageT
		) {
			val nodeAndPlayerContext = nodes[inetSocketAddress] ?: return
			val node = nodeAndPlayerContext.first
			node.approveMessage(message)
		}

		fun stateHandle(
			address: InetSocketAddress, message: MessageT, msgT: MessageType
		) {

		}

		fun discoverHandle(
			address: InetSocketAddress, message: MessageT, msgT: MessageType
		) {
			TODO("Not yet implemented")
		}

		fun roleChangeHandle(
			address: InetSocketAddress, message: MessageT, msgT: MessageType
		) {
			context.run {
				val msg = messageTranslator.fromMessageT(message, msgT)
				msg.senderId ?: return
				msg.receiverId ?: return
				val (node, context) = nodes[address] ?: return
				if (context.playerId != msg.senderId ||)
			}
		}

		fun announcementHandle(
			address: InetSocketAddress, message: MessageT, msgT: MessageType
		) {
			TODO("Not yet implemented")
		}

		fun errorHandle(
			address: InetSocketAddress, message: MessageT, msgT: MessageType
		) {
			TODO("Not yet implemented")
		}

		fun steerHandle(
			address: InetSocketAddress, message: MessageT, msgT: MessageType
		) {
			if (context.nodeState != NodeRole.MASTER) {
				return;
			}
			TODO("Not yet implemented")
		}
	}
}
