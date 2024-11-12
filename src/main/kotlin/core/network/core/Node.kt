package d.zhdanov.ccfit.nsu.core.network.core

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.P2PMessage
import d.zhdanov.ccfit.nsu.core.network.exceptions.IllegalUnacknowledgedMessagesGetAttempt
import d.zhdanov.ccfit.nsu.core.network.interfaces.Node
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

/**
 * todo fix doc
 * ContextNode class represents a node in a peer-to-peer context.
 *
 * Each node is responsible for monitoring the delivery of sent messages.
 * A message is resent if the [resendDelay] is exceeded.
 * A node is considered dead if the message delay exceeds [thresholdDelay].
 */
class Node<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
  initialState: StateHolder.InternalNodeState = StateHolder.InternalNodeState.Booting,
  initMsgSeqNum: Long,
  messageComparator: Comparator<MessageT>,
  observerScope: CoroutineScope,
  @Volatile override var nodeRole: NodeRole,
  override val id: Int,
  override val address: InetSocketAddress,
  private val resendDelay: Long,
  private val thresholdDelay: Long,
  private val nodesHandler: NodesHandler<MessageT, InboundMessageTranslator, Payload>
) : Node<InetSocketAddress> {
  private val state =
    StateHolder<MessageT, InboundMessageTranslator>(initialState)
  private val msgSeqNum: AtomicLong = AtomicLong(initMsgSeqNum)
  private val roleChangeChannel = Channel<P2PMessage>()

  /**
   * Change this values within the scope of synchronized([msgForAcknowledge]).
   */
  private val msgForAcknowledge: TreeMap<MessageT, Long> =
    TreeMap(messageComparator)
  @Volatile private var lastReceive = 0L
  @Volatile private var lastSend = 0L

  init {

  }


  class StateHolder<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
    state: InternalNodeState
  ) {
    private val state = AtomicReference(state)

    /**
     * Represents the various states of a node in the system.
     */
    enum class InternalNodeState {
      Booting,
      Operational,
      StartingFinalization,
      FinalizingProcess,
      FinalizedState,
      Terminated;
    }

    fun CoroutineScope.startObservation(node: d.zhdanov.ccfit.nsu.core.network.core.Node<MessageT, InboundMessageTranslator>): Job {
      return launch {
        while(true) {
          when(state.get()) {
            InternalNodeState.Booting     -> onRegistration(node)
            InternalNodeState.Operational -> onOperational(node)
            InternalNodeState.Finalizing  -> {}
            InternalNodeState.Finalized   -> {}
            InternalNodeState.Terminated  -> {}
            else                          -> {

            }
          }
        }
      }
    }

    fun onEvent() {

    }

    /**
     * @return [List]`<MessageT>` node [InternalNodeState] == [InternalNodeState.Terminated]
     * @throws IllegalUnacknowledgedMessagesGetAttempt if [InternalNodeState] !=
     * [InternalNodeState.Terminated]
     * */
    private fun getUnacknowledgedMessages(
      node: d.zhdanov.ccfit.nsu.core.network.core.Node<MessageT, InboundMessageTranslator>
    ): List<MessageT> {
      synchronized(node.msgForAcknowledge) {
        if(state.get() != InternalNodeState.Terminated) {
          throw IllegalUnacknowledgedMessagesGetAttempt(
            InternalNodeState.Terminated
          )
        }
        return node.msgForAcknowledge.keys.toList();
      }
    }

    private suspend fun onRegistration(
      node: d.zhdanov.ccfit.nsu.core.network.core.Node<MessageT, InboundMessageTranslator>
    ) {
      val nextDelay = synchronized(node.msgForAcknowledge) {
        if(state.get() == InternalNodeState.Booting) return

        val now = System.currentTimeMillis()
        if(now - node.lastReceive < node.thresholdDelay) {
          return state.set(InternalNodeState.Terminated)

        }
        val nextDelay = node.checkMessages()
        node.sendPingIfNecessary(nextDelay, now)

        return@synchronized nextDelay
      }
      delay(nextDelay)
    }

    private suspend fun onOperational(
      node: d.zhdanov.ccfit.nsu.core.network.core.Node<MessageT, InboundMessageTranslator>
    ) {
      val nextDelay = synchronized(node.msgForAcknowledge) {
        if(state.get() != InternalNodeState.Operational) return

        val now = System.currentTimeMillis()
        if(now - node.lastReceive > node.thresholdDelay) {
          return state.set(InternalNodeState.Terminated)
        }

        val nextDelay = node.checkMessages()
        node.sendPingIfNecessary(nextDelay, now)

        return@synchronized nextDelay
      }
      delay(nextDelay)
    }

    private suspend fun onWaitToFinalize(
      node: d.zhdanov.ccfit.nsu.core.network.core.Node<MessageT, InboundMessageTranslator>
    ) {
      val nextDelay = synchronized(node.msgForAcknowledge) {
        if(state.get() != InternalNodeState.Finalizing) return
        if(node.msgForAcknowledge.isEmpty()) return
        return@synchronized node.checkMessages()
      }
      delay(nextDelay)
    }
  }

  private fun sendPingIfNecessary(nextDelay: Long, now: Long) {
    if(!(nextDelay == resendDelay && now - lastSend >= resendDelay)) return

    val seq = getNextMSGSeqNum()
    val ping = nodesHandler.msgUtils.getPingMsg(seq)
    nodesHandler.sendUnicast(ping, this)
    lastSend = System.currentTimeMillis()
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
        nodesHandler.sendUnicast(msg, this)
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


}