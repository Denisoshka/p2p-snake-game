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
 */
class Node<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
  initialState: InitialState = InitialState.Registration,
  initMsgSeqNum: Long,
  messageComparator: Comparator<MessageT>,
  nodeCoroutineContext: CoroutineScope,
  @Volatile override var nodeRole: NodeRole,
  override val id: Int,
  override val address: InetSocketAddress,
  private val resendDelay: Long,
  private val thresholdDelay: Long,
  private val nodesContext: NodesContext<MessageT, InboundMessageTranslator>
) : Node<InetSocketAddress> {
  /**
   * Represents the various states of a node in the system.
   */
  enum class InitialState {
    /**
     * Indicates that the node is pending registration.
     * In this state, the node needs to be registered within the system's context
     * before it can transition to an active state.
     */
    Registration,

    /**
     * Indicates that the node is fully operational.
     * The node is registered, active, and does not require further actions to maintain its state.
     */
    Operational,

    /**
     * Indicates that the node is in the finalizing stage.
     * The node needs to acknowledge and confirm all pending messages before it can terminate or transition
     * to another state.
     */
    Finalizing
  }

  enum class NodeState {
    NeedRegistration,
    Active,
    WaitTermination,
    Terminated,
  }

  @Volatile var nodeState: NodeState
    private set

  private val msgSeqNum: AtomicLong = AtomicLong(initMsgSeqNum)
  private val roleChangeChannel = Channel<P2PMessage>()
  private val observationJob: Job
  private val selectJob: Job

  /**
   * Change this values within the scope of synchronized([msgForAcknowledge]).
   */
  private val msgForAcknowledge: TreeMap<MessageT, Long> =
    TreeMap(messageComparator)
  @Volatile private var lastReceive = 0L
  @Volatile private var lastSend = 0L

  init {
    if(initialState != InitialState.Operational) {
      Preconditions.checkArgument(
        nodeRole == NodeRole.VIEWER || nodeRole == NodeRole.NORMAL,
        "on initialState==InitialState.Registration/InitialState.Finalizing " + "nodeRole must be NodeRole.VIEWER/NodeRole.NORMAL"
      )
    }

    nodeState = when(initialState) {
      InitialState.Registration -> NodeState.NeedRegistration
      InitialState.Operational  -> NodeState.Active
      InitialState.Finalizing   -> NodeState.WaitTermination
    }
    observationJob = nodeCoroutineContext.launch {
      try {
        while(this.isActive) {
          when(nodeState) {
            NodeState.NeedRegistration -> onRegistration()
            NodeState.Active           -> onActive()
            NodeState.WaitTermination  -> onWaitTermination()
            NodeState.Terminated       -> {
              nodesContext.handleNodeTermination(this@Node)
              break
            }
          }
        }
      } catch(_: CancellationException) {
        this.cancel()
      }
    }
    selectJob = nodeCoroutineContext.launch {
      try {
        while(true) {
          select {
            roleChangeChannel.onReceive {
              nodesContext.handleNodeRoleChange(this@Node, it)
            }
          }
        }
      } catch(_: CancellationException) {
        this.cancel()
      }
    }
  }

  private suspend fun onRegistration() {
    while(nodeState == NodeState.NeedRegistration) {
      val nextDelay = synchronized(msgForAcknowledge) {
        if(nodeState != NodeState.NeedRegistration) return
        val now = System.currentTimeMillis()
        if(now - lastReceive < thresholdDelay) {
          nodeState = NodeState.Terminated
          return
        }

        val nextDelay = checkMessages()
        sendPingIfNecessary(nextDelay, now)

        return@synchronized nextDelay
      }
      delay(nextDelay)
    }
  }

  private suspend fun onActive() {
    while(nodeState == NodeState.Active) {
      val nextDelay = synchronized(msgForAcknowledge) {
        if(nodeState != NodeState.Active) return

        val now = System.currentTimeMillis()
        if(now - lastReceive > thresholdDelay) {
          nodeState = NodeState.Terminated
          return
        }

        val nextDelay = checkMessages()
        sendPingIfNecessary(nextDelay, now)

        return@synchronized nextDelay
      }

      delay(nextDelay)
    }
  }

  private fun sendPingIfNecessary(nextDelay: Long, now: Long) {
    if(!(nextDelay == resendDelay && now - lastSend >= resendDelay)) return

    val seq = getNextMSGSeqNum()
    val ping = nodesContext.msgUtils.getPingMsg(seq)
    nodesContext.sendUnicast(ping, this)
    lastSend = System.currentTimeMillis()
  }

  private suspend fun onWaitTermination() {
    while(nodeState == NodeState.WaitTermination) {
      val nextDelay = synchronized(msgForAcknowledge) {
        if(nodeState != NodeState.WaitTermination) return
        if(msgForAcknowledge.isEmpty()) return
        return@synchronized checkMessages()
      }
      delay(nextDelay)
    }
  }

  private fun checkMessages(): Long {
    var ret = resendDelay
    val now = System.currentTimeMillis()
    val it = msgForAcknowledge.iterator()
    while(it.hasNext()) {
      val entry = it.next()
      val (msg, time) = entry
      if(now - time < thresholdDelay) {
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
    if(nodeState != NodeState.Active) return false
    synchronized(msgForAcknowledge) {
      msgForAcknowledge[message] = System.currentTimeMillis()
      lastSend = System.currentTimeMillis()
    }
    return true
  }

  fun addAllMessageForAcknowledge(messages: List<MessageT>): Boolean {
    if(nodeState != NodeState.Active) return false
    synchronized(msgForAcknowledge) {
      for(msg in messages) {
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
      if(nodeState != NodeState.Terminated) {
        throw IllegalUnacknowledgedMessagesGetAttempt(nodeState)
      }
      return msgForAcknowledge.keys.toList();
    }
  }

  fun shutdownNow() {
    synchronized(msgForAcknowledge) {
      nodeState = NodeState.Terminated
    }
  }

  fun shutdown(status: MessageT? = null) {
    synchronized(msgForAcknowledge) {
      status?.also {
        msgForAcknowledge[it] = System.currentTimeMillis()
        lastSend = System.currentTimeMillis()
      }
      if(nodeState != NodeState.Terminated) {
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