package d.zhdanov.ccfit.nsu.core.network

import com.google.common.base.Preconditions
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeContext
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

/**
 * Changes the state of the cluster in a single thread, so that if any
 * other node fails during the state change, it will be taken into account.
 *
 * This ensures that the system remains consistent and can handle
 * failures gracefully. The following scenarios illustrate how nodes
 * manage role transitions based on their current state:
 * Describes the role management and transitions in a distributed game system.
 *
 * The following scenarios outline how nodes handle the failure
 * of their peers based on their roles:
 *
 * a) **Node [NodeRole.NORMAL] notices that [NodeRole.MASTER] has dropped.**
 * The node replaces the information about the central node with the
 * [NodeRole.DEPUTY]. It starts sending all unicast messages toward the
 * [NodeRole.DEPUTY].
 *
 * b) **Node [NodeRole.MASTER] notices that [NodeRole.DEPUTY] has dropped.**
 * The [NodeRole.MASTER] node selects a new [NodeRole.DEPUTY] from among
 * the [NodeRole.NORMAL] nodes. It informs the current DEPUTY about this
 * change using a
 * [d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg].
 * Other nodes learn about the new [NodeRole.DEPUTY] from a scheduled
 * [d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg],
 * as it is not urgent for them to know immediately.
 *
 * c) **Node with [NodeRole.DEPUTY] role notices that [NodeRole.MASTER] has
 * dropped.** The DEPUTY node becomes the [NodeRole.MASTER] (takes
 * over control of the game). It selects a new [NodeRole.DEPUTY]
 * and informs all players about this change using a
 * [d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg].
 *
 * ***Message regarding role changes in the game.***
 *
 * **This message can take different forms depending on the roles of the
 * participants and their actions:**
 *
 * 1. **From a Deputy** to other players indicating that they should start
 * considering him as the Master. ( *senderRole* = [NodeRole.MASTER])
 *
 * 2. **From an intentionally exiting player**.
 * (*senderRole* = [NodeRole.VIEWER])
 *
 * 3. **From the Master** to a deceased player.
 * (*receiverRole* = [NodeRole.VIEWER])
 *
 * 4. **Appointment of someone as Deputy**.
 * In combination with 1, 2, or separately
 * (*receiverRole* = [NodeRole.DEPUTY])
 *
 * 5. In combination with 2, from the Master to the Deputy indicating
 * that he is becoming the Master.
 * (*receiverRole* = [NodeRole.MASTER])
 */
class NodesContext<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
	joinBacklog: Int,
	addrAndMsg: Pair<InetSocketAddress, P2PMessage>?,
	private val p2p: P2PContext<MessageT, InboundMessageTranslator>,
	private val messageTranslator: InboundMessageTranslator,
	@Volatile private var nodeRole: NodeRole/*todo ну или это значение используется только в контексте когда мы смотрим
      ноды или его нужно хранить вместе с мастером и депутей*/
) : NodeContext<MessageT, InboundMessageTranslator> {
	private val localNodeId: Int
	private val nodesScope = CoroutineScope(Dispatchers.Default)
	private val nodesByIp: ConcurrentHashMap<InetSocketAddress, Node<MessageT, InboundMessageTranslator>> =
		ConcurrentHashMap()
	private val nodesById: ConcurrentHashMap<Int, Node<MessageT, InboundMessageTranslator>> =
		ConcurrentHashMap()
	private val deadNodeChannel: Channel<Node<MessageT, InboundMessageTranslator>> =
		Channel(joinBacklog)
	private val registerNewNode: Channel<Node<MessageT, InboundMessageTranslator>> =
		Channel(joinBacklog)
	private val reconfigureContext: Channel<Pair<Node<MessageT,
			InboundMessageTranslator>, P2PMessage>> =
		Channel(joinBacklog)
	private val masterAndDeputy: AtomicReference<Pair<Int, Int?>> =
		AtomicReference()

	init {
		if (addrAndMsg != null) {
			Preconditions.checkArgument(
				nodeRole == NodeRole.MASTER || nodeRole == NodeRole.DEPUTY,
				"$nodeRole not in allowed roles [NodeRole.MASTER, NodeRole.DEPUTY]"
			)
			val (addr, msg) = addrAndMsg
			val masterId = Preconditions.checkNotNull(
				msg.senderId, "require senderId not null"
			)
			localNodeId = Preconditions.checkNotNull(
				msg.receiverId, "require receiverId not null"
			)
			val masterNode = Node(
				msg.msgSeq,
				p2p.messageUtils.getComparator(),
				nodesScope,
				NodeRole.MASTER,
				masterId,
				addr,
				p2p.resendDelay,
				p2p.thresholdDelay,
				this
			)
			masterAndDeputy.set(Pair(masterId, null))
			addNode(masterNode)
		} else {
			localNodeId = 0
			masterAndDeputy.set(Pair(localNodeId, null))
			nodeRole = NodeRole.MASTER
		}
		launchNodesWatcher()
	}


	fun destroy() {
		nodesScope.cancel()
		nodesByIp.clear()
		nodesById.clear()
	}

	/*
	* мы меняем состояние кластера в одной функции так что исполнение линейно
	* */
	private fun launchNodesWatcher(): Job {
		return nodesScope.launch {
			while (true) {
				select {
					deadNodeChannel.onReceive { node ->
						onNodeTermination(node)
					}          /*registerNewNode.onReceive { node ->
						handleNodeRegistration(node)
					}*/
					reconfigureContext.onReceive { (node, msg) ->
						onNodeGoOut(node, msg)
					}
				}
			}
		}
	}

	override fun handleNodeRegistration(node: Node<MessageT, InboundMessageTranslator>) {
	}

	override suspend fun handleNodeTermination(
		node: Node<MessageT, InboundMessageTranslator>
	) = deadNodeChannel.send(node)

	override suspend fun handleNodeRoleChange(
		node: Node<MessageT, InboundMessageTranslator>, p2pRoleChange: P2PMessage
	) = reconfigureContext.send(Pair(node, p2pRoleChange))


	private fun onNodeTermination(node: Node<MessageT, InboundMessageTranslator>) {
		/**
		 * [NodeRole] :
		 * -> [NodeRole.NORMAL],
		 * -> [NodeRole.MASTER],
		 * -> [NodeRolez.DEPUTY],
		 * -> [NodeRole.VIEWER]
		 */
		val (masterId, deputyId) = masterAndDeputy.get()
		deleteNode(node)
		if (localNodeId == masterId) {
			if (node.id == deputyId) masterOnDeputyDead(node)
		} else if (localNodeId == deputyId) {
			if (node.id == masterId) deputyOnMasterDead(node)
		} else if (nodeRole == NodeRole.NORMAL) {
			if (node.id == masterId) normalOnMasterDead(node)/* ну а другие ноды мы и не трекаем*/
		} else {      /*а ну мы же просто выкидываем вивера и похуй)*/
			logger.debug { node.toString() }/*todo нужно сделать просто выход из изры ибо мы вивер*/
		}
	}

	private fun onNodeRegistration(
		node: Node<MessageT, InboundMessageTranslator>
	) {
	}

	/**
	 * Perform logic only when [NodesContext.nodeRole]==[NodeRole.MASTER]
	 * other msgs handle higher
	 */
	private fun onNodeGoOut(
		node: Node<MessageT, InboundMessageTranslator>,
		roleChangeMsg: P2PMessage
	) {
		val (_, deputyId) = masterAndDeputy.get()
		if (nodeRole != NodeRole.MASTER) {
			logger.debug { "only for master" }
			return
		}
		node.nodeRole = NodeRole.VIEWER
		if (node.id == deputyId) {
			masterSetNewDeputy(node)?.also {
				val p2pMsg = getRoleChangeMsg(
					NodeRole.MASTER,
					NodeRole.DEPUTY,
					it.getNextMSGSeqNum(),
					localNodeId,
					it.id
				)
				val msg = messageTranslator.toMessageT(
					p2pMsg, MessageType.RoleChangeMsg
				)
				sendWithAck(msg, it)
			}
		}
		val status = p2p.messageUtils.getAckMsg(
			roleChangeMsg.msgSeq, localNodeId, node.id
		)
		node.shutdown(status);
	}

	private fun normalOnMasterDead(master: Node<MessageT, InboundMessageTranslator>) {
		/**
		 * Processes the current state received from the cluster.
		 *
		 * If we are aware of other nodes in the cluster but do not receive notification
		 * of a new leader election, we assume that we have been disconnected from the cluster.
		 *
		 * However, if we appear to be the only remaining node, we designate ourselves as the master.
		 */
		val (_, deputyId) = masterAndDeputy.get()
		if (deputyId == null) {
			takeOverTheBoard()
		} else {
			nodesById[deputyId]?.also {
				val nonAckMsgs = master.getUnacknowledgedMessages()
				for (msg in nonAckMsgs) p2p.sendUnicast(msg, it)
				it.addAllMessageForAcknowledge(nonAckMsgs)
				return
			}
			throw RuntimeException("какого хуя у нас нет депути")
		}
	}

	private fun deputyOnMasterDead(master: Node<MessageT, InboundMessageTranslator>) {
		nodeRole = NodeRole.MASTER
		val newDeputy = masterSetNewDeputy(master) ?: return
		for ((_, node) in nodesByIp) {
			if (newDeputy == node) continue
			val p2pMsg = getRoleChangeMsg(
				NodeRole.MASTER,
				null,
				newDeputy.getNextMSGSeqNum(),
				localNodeId,
				node.id
			)
			val msg = messageTranslator.toMessageT(p2pMsg, MessageType.RoleChangeMsg)
			sendWithAck(msg, node)
		}
		val p2pMsg = getRoleChangeMsg(
			NodeRole.MASTER,
			NodeRole.DEPUTY,
			newDeputy.getNextMSGSeqNum(),
			localNodeId,
			newDeputy.id
		)
		val msg = messageTranslator.toMessageT(p2pMsg, MessageType.RoleChangeMsg)
		sendWithAck(msg, newDeputy)
	}

	private fun getRoleChangeMsg(
		sendRole: NodeRole?,
		recvRole: NodeRole?,
		seqNum: Long,
		sendId: Int,
		recId: Int
	): P2PMessage {
		val role = RoleChangeMsg(sendRole, recvRole)
		return P2PMessage(seqNum, role, sendId, recId)
	}

	private fun masterSetNewDeputy(
		oldDeputy: Node<MessageT, InboundMessageTranslator>
	): Node<MessageT, InboundMessageTranslator>? {
		val newDeputy = chooseNewDeputy(oldDeputy)
		newDeputy?.nodeRole = NodeRole.DEPUTY
		masterAndDeputy.set(Pair(localNodeId, newDeputy?.id))
		return newDeputy
	}

	/**
	 * Selects a new deputy, assigns it to [oldDeputy], and returns it. If no
	 * suitable candidate for deputy is found, assigns and returns `null`.
	 *
	 * @return [oldDeputy] if a deputy was chosen, otherwise `null`.
	 */
	private fun chooseNewDeputy(oldDeputy: Node<MessageT, InboundMessageTranslator>): Node<MessageT, InboundMessageTranslator>? {
		for ((_, node) in nodesByIp) {
			if (node.id == oldDeputy.id || node.nodeRole != NodeRole.NORMAL || node.nodeState != Node.NodeState.Active) continue
			return node
		}
		return null
	}

	private fun takeOverTheBoard() {

	}

	private fun deleteNode(node: Node<MessageT, InboundMessageTranslator>) {
		nodesByIp.remove(node.address)
		nodesById.remove(node.id)
	}

	private fun addNode(masterNode: Node<MessageT, InboundMessageTranslator>) {
		nodesByIp[masterNode.address] = masterNode
		nodesById[masterNode.id] = masterNode
	}

	private fun sendWithAck(
		msg: MessageT, node: Node<MessageT, InboundMessageTranslator>
	) {
		p2p.sendUnicast(msg, node)
		node.addMessageForAcknowledge(msg)
	}

	private fun masterOnDeputyDead(deputy: Node<MessageT, InboundMessageTranslator>) {
		val newDeputy = masterSetNewDeputy(deputy) ?: return
		val p2pMsg = getRoleChangeMsg(
			NodeRole.MASTER,
			NodeRole.DEPUTY,
			newDeputy.getNextMSGSeqNum(),
			localNodeId,
			newDeputy.id
		)
		val msg = messageTranslator.toMessageT(p2pMsg, MessageType.RoleChangeMsg)
		sendWithAck(msg, newDeputy)
	}
}
