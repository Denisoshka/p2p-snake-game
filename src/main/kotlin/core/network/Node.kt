package d.zhdanov.ccfit.nsu.core.network

import com.google.common.base.Preconditions
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.network.exceptions.IllegalUnacknowledgedMessagesGetAttempt
import d.zhdanov.ccfit.nsu.core.network.interfaces.Node
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/**
 * todo fix doc
 * ContextNode class represents a node in a peer-to-peer context.
 *
 * Each node is responsible for monitoring the delivery of sent messages.
 * A message is resent if the [resendDelay] is exceeded.
 * A node is considered dead if the message delay exceeds [thresholdDelay].
 *
 * @param address The unique identifier for the node, represented as an InetSocketAddress.
 * @param resendDelay Delay in milliseconds before resending a message.
 * @param thresholdDelay Threshold delay in milliseconds to determine node state.
 * @param context The P2P context which manages the node's interactions.
 * @param messageComparator Comparator for comparing messages of type [MessageT]. This comparator must compare messages based on the sequence number msg_seq, which is unique to the node within the context and monotonically increasing.
 * @param nodeCoroutineScope Coroutine context for checking the node state. Default is [Dispatchers.IO].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Node<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
	initMsgSeqNum: Long,
	messageComparator: Comparator<MessageT>,
	nodeCoroutineContext: CoroutineScope,
	@Volatile var nodeRole: NodeRole,
	override val id: Int,
	override val address: InetSocketAddress,
	private val resendDelay: Long,
	private val thresholdDelay: Long,
	private val nodesContext: NodesContext<MessageT, InboundMessageTranslator>
) : Node<InetSocketAddress> {
	enum class NodeState {
		WaitRegistration,
		Active,
		WaitTermination,
		Terminated,
	}

	@Volatile var nodeState: NodeState = NodeState.Active
		private set

	private val msgSeqNum: AtomicLong = AtomicLong(initMsgSeqNum)
	private val roleChangeChannel = Channel<P2PMessage>()
	private var observationJob: Job? = null
	private var selectJob: Job? = null

	/**
	 * Change this values within the scope of synchronized([msgForAcknowledge]).
	 */
	private val msgForAcknowledge: TreeMap<MessageT, Long> =
		TreeMap(messageComparator)
	@Volatile private var lastReceive = 0L
	@Volatile private var lastSend = 0L

	init {
		val delayCoef = 0.8
		observationJob = nodeCoroutineContext.launch {
			try {
				while (true) {
					when (nodeState) {
						NodeState.WaitRegistration -> {}
						NodeState.Active -> onActive()
						NodeState.WaitTermination -> onWaitTermination()
						NodeState.Terminated -> {
							nodesContext.handleNodeTermination(this@Node)
							break
						}
					}
				}
			} catch (_: CancellationException) {
				this.cancel()
			}
		}
		selectJob = nodeCoroutineContext.launch {
			while (true) {
				select {
					roleChangeChannel.onReceive {
						nodesContext.handleNodeRoleChange(this@Node, it)
					}
				}
			}
		}
	}

	private suspend fun onActive() {
		while (nodeState == NodeState.Active) {
			val nextDelay = synchronized(msgForAcknowledge) {
				if (nodeState != NodeState.Active) return

				val now = System.currentTimeMillis()
				if (now - lastReceive > thresholdDelay) {
					nodeState = NodeState.Terminated
					return
				}

				val nextDelay = checkMessages()
				if (nextDelay == resendDelay && now - lastSend >= resendDelay) {
					val seq = getNextMSGSeqNum()
					val ping = nodesContext.msgUtils.getPingMsg(seq)
					nodesContext.sendUnicast(ping, this)
					lastSend = System.currentTimeMillis()
				}

				return@synchronized nextDelay
			}

			delay(nextDelay)
		}
	}

	private suspend fun onWaitTermination() {
		while (nodeState == NodeState.WaitTermination) {
			val nextDelay = synchronized(msgForAcknowledge) {
				if (nodeState != NodeState.WaitTermination) return
				if (msgForAcknowledge.isEmpty()) return
				return@synchronized checkMessages()
			}
			delay(nextDelay)
		}
	}

	private fun checkMessages(): Long {
		var ret = resendDelay
		val now = System.currentTimeMillis()
		val it = msgForAcknowledge.iterator()
		while (it.hasNext()) {
			val entry = it.next()
			val (msg, time) = entry
			if (now - time < thresholdDelay) {
				ret = ret.coerceAtMost(thresholdDelay + time - now)
				entry.setValue(now)
				nodesContext.sendUnicast(msg, this)
			} else {
				it.remove()
			}
		}
		return ret
	}

	fun approveMessage(message: MessageT) {
		synchronized(msgForAcknowledge) {
			msgForAcknowledge.remove(message)
			lastReceive = System.currentTimeMillis()
		}
	}

	/**
	 * @return `false` if node [NodeState] != [NodeState.Active] else `true`
	 */
	fun addMessageForAcknowledge(message: MessageT): Boolean {
		if (nodeState != NodeState.Active) return false
		synchronized(msgForAcknowledge) {
			msgForAcknowledge[message] = System.currentTimeMillis()
			lastSend = System.currentTimeMillis()
		}
		return true
	}

	fun addAllMessageForAcknowledge(messages: List<MessageT>): Boolean {
		if (nodeState != NodeState.Active) return false
		synchronized(msgForAcknowledge) {
			for (msg in messages) {
				msgForAcknowledge[msg] = System.currentTimeMillis()
			}
			lastSend = System.currentTimeMillis()
		}
		return true
	}

	fun getNextMSGSeqNum(): Long {
		return msgSeqNum.incrementAndGet()
	}

	/**
	 * @return [List]`<MessageT>` node [NodeState] == [NodeState.Terminated]
	 * @throws IllegalUnacknowledgedMessagesGetAttempt if [NodeState] !=
	 * [NodeState.Terminated]
	 * */
	fun getUnacknowledgedMessages(): List<MessageT> {
		synchronized(msgForAcknowledge) {
			if (nodeState != NodeState.Terminated) {
				throw IllegalUnacknowledgedMessagesGetAttempt(nodeState)
			}
			return msgForAcknowledge.keys.toList();
		}
	}

	fun shutdownNow() {
		observationJob.cancel()
		selectJob.cancel()
	}

	fun shutdown(status: MessageT? = null) {
		synchronized(msgForAcknowledge) {
			status?.also {
				msgForAcknowledge[it] = System.currentTimeMillis()
				lastSend = System.currentTimeMillis()
			}
			if (nodeState != NodeState.Terminated) {
				nodeState = NodeState.WaitTermination
			}
		}
	}


	/**
	 * in cur version perform logic only with [NodeRole.VIEWER] request
	 * @throws IllegalArgumentException if [p2pRoleChangeMsg]!=[NodeRole.VIEWER]
	 * @throws IllegalArgumentException if [p2pRoleChangeMsg] not perform logout
	 * @param p2pRoleChangeMsg new role of node
	 * @return `true` if new role was submitted successfully, else `false`
	 * */
	fun submitNewNodeRole(p2pRoleChangeMsg: P2PMessage): Boolean {
		Preconditions.checkArgument(
			p2pRoleChangeMsg.msg.type == MessageType.RoleChangeMsg,
			"perform logic only with MessageType.RoleChangeMsg"
		)
		val roleChange = p2pRoleChangeMsg.msg as RoleChangeMsg
		Preconditions.checkArgument(
			roleChange.senderRole != NodeRole.VIEWER && roleChange.receiverRole == null,
			"perform logic only with logout msg"
		)
		return roleChangeChannel.trySend(p2pRoleChangeMsg).isSuccess
	}

	override fun toString(): String {
		return "Node(id=$id, address=$address, nodeState=$nodeState, nodeRole=$nodeRole)"
	}
}