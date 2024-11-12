package core.network.nodes

import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalStateMachineStateIsNull
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalUnacknowledgedMessagesGetAttempt
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT.NodeEvent
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}
private const val NodeAlreadyRegisteredMsg =
  "node already registered in cluster"
private const val IncorrectRegisterEvent =
  "registration channel receive wrong event: "

/**
 * todo fix doc
 * ContextNode class represents a node in a peer-to-peer context.
 *
 * Each node is responsible for monitoring the delivery of sent messages.
 * A message is resent if the [resendDelay] is exceeded.
 * A node is considered dead if the message delay exceeds [thresholdDelay].
 */
class Node<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
  initMsgSeqNum: Long, messageComparator: Comparator<MessageT>,
  observerScope: CoroutineScope,
  @Volatile override var nodeState: NodeT.NodeState, override val id: Int,
  override val address: InetSocketAddress,
  private val nodesHandler: NodesHandler<MessageT, InboundMessageTranslator, Payload>
) : NodeT<InetSocketAddress> {
  val resendDelay: Long = nodesHandler.resendDelay
  val thresholdDelay: Long = nodesHandler.thresholdDelay
  @Volatile private var lastReceive: Long = System.currentTimeMillis()
  @Volatile private var lastSend: Long = System.currentTimeMillis()
  private val msgSeqNum: AtomicLong = AtomicLong(initMsgSeqNum)
  private val observeJob : Job
  private val state = StateHolder<MessageT, InboundMessageTranslator, Payload>(
    TODO()
  )

  init{
    observeJob = observerScope.launch {
    }

  }

  /**
   * Change this values within the scope of synchronized([msgForAcknowledge]).
   */
  private val msgForAcknowledge: TreeMap<MessageT, Long> = TreeMap(
    messageComparator
  )

  private fun sendPingIfNecessary(nextDelay: Long, now: Long) {
    if(!(nextDelay == resendDelay && now - lastSend >= resendDelay)) return

    val seq = getNextMSGSeqNum()

    val ping = nodesHandler.msgUtils.getPingMsg(seq)
    nodesHandler.sendUnicast(ping, address)
    lastSend = System.currentTimeMillis()
  }

  fun approveMessage(message: MessageT) {
    synchronized(msgForAcknowledge) {
      msgForAcknowledge.remove(message)
      lastReceive = System.currentTimeMillis()
    }
  }

  fun addMessageForAcknowledge(message: MessageT): Boolean {
    synchronized(msgForAcknowledge) {
      msgForAcknowledge[message] = System.currentTimeMillis()
      lastSend = System.currentTimeMillis()
    }
    return true
  }

  fun addAllMessageForAcknowledge(messages: List<MessageT>) {
    synchronized(msgForAcknowledge) {
      for(msg in messages) {
        msgForAcknowledge[msg] = System.currentTimeMillis()
      }
      lastSend = System.currentTimeMillis()
    }
  }

  fun handleEvent(event: NodeEvent) = state.onEvent(event)

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
        nodesHandler.sendUnicast(msg, address)
      } else {
        it.remove()
      }
    }
    return ret
  }

  fun getUnacknowledgedMessages(): List<MessageT> {
    return state.getUnacknowledgedMessages(this)
  }

  fun getNextMSGSeqNum(): Long {
    return msgSeqNum.incrementAndGet()
  }

  private class StateHolder<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
    state: InternalNodeState
  ) {
    private val currState = AtomicReference(state)
    private var bootChannel: Channel<NodeEvent> = Channel()

    /**
     * Represents the various states of a node in the system.
     * ## State Diagram:
     * ```
     *  ┌───────┐
     *  │Booting│───────────────────►┐
     *  └──┬────┘                    │
     *     ▼                         │
     *  ┌──────────┐                 ▼
     *  │Processing│────────────────►┤
     *  └──┬───────┘                 │
     *     ▼                         │
     *  ┌─────────────────┐          │
     *  │FinalizationStart│          │
     *  └──┬──────────────┘          │
     *     ▼                         │
     *  ┌─────────────────┐          ▼
     *  │FinalizingProcess│─────────►┤
     *  └──┬──────────────┘          │
     *     ▼                         │
     *  ┌──────────────┐             │
     *  │FinalizedState│             │
     *  └──┬───────────┘             │
     *     ▼                         │
     *   ┌──────────┐                ▼
     *   │Terminated│◄───────────────┘
     *   └──────────┘
     * ```
     */
    enum class InternalNodeState {
      Booting,
      Processing,
      FinalizationStart,
      FinalizingProcess,
      FinalizedState,
      Terminated;
    }

    fun CoroutineScope.startObservation(
      node: Node<MessageT, InboundMessageTranslator, Payload>
    ): Job {
      return launch {
        if(currState.get() == InternalNodeState.Booting) {
          launch {
            val ret = withTimeoutOrNull(10) {
              bootChannel.receive()
            }
            if(ret != null && ret != NodeEvent.NodeRegistered) {
              changeState(InternalNodeState.Processing)
            } else if(ret == null) {
//              todo make error send here
              changeState(InternalNodeState.Terminated)
            } else {
              throw IllegalNodeRegisterAttempt(IncorrectRegisterEvent)
            }
          }
        }
        while(true) {
          when(currState.get()) {
            InternalNodeState.Booting           -> {
              val ret = onBooting(node)
              delay(ret)
            }

            InternalNodeState.Processing        -> {
              val ret = onProcessing(node)
              delay(ret)
            }

            InternalNodeState.FinalizationStart -> {
              if(changeState(InternalNodeState.FinalizingProcess)) {
                node.nodeState = NodeT.NodeState.Disabled
                node.nodesHandler.prepareForDetachNode(node)
              }
            }

            InternalNodeState.FinalizingProcess -> {
              val ret = onFinalizingProcess(node)
              delay(ret)
            }

            InternalNodeState.FinalizedState    -> {
              node.nodeState = NodeT.NodeState.Disabled
              currState.set(InternalNodeState.Terminated)
            }

            InternalNodeState.Terminated        -> {
              node.nodesHandler.detachNode(node)
              break
            }

            else                                -> {
              logger.error { "state machine state is null" }
              throw IllegalStateMachineStateIsNull()
            }
          }
        }
      }
    }

    /**
     * Atomically sets a new state in the state machine, ensuring state hierarchy consistency.
     *
     * Attempts to update `statemachineState` to the specified `newState`. This operation succeeds
     * only if the current state in `statemachineState` has not changed during execution
     * and is lower in the hierarchy than `newState`.
     *
     * @param newState The new state to set, which should be hierarchically higher than the current state.
     * @return `true` if the state was successfully updated to `newState`;
     *         `false` if `newState` is less than or equal to the current state, in which case
     *         the update is prevented to maintain the hierarchical order.
     */
    private fun changeState(newState: InternalNodeState): Boolean {
      do {
        val prevState = currState.get()
        if(newState <= prevState) return false;
      } while(currState.compareAndSet(prevState, newState))
      return true
    }

    fun onEvent(event: NodeEvent) {
      when(event) {
        NodeEvent.NodeRegistered              -> {
          if(currState.get() != InternalNodeState.Booting) throw IllegalNodeRegisterAttempt()
          bootChannel.run {
            val ret = trySend(event)
            if(ret.isFailure || ret.isClosed) {
              throw IllegalNodeRegisterAttempt(NodeAlreadyRegisteredMsg)
            }
          }
        }

        NodeEvent.ShutdownFromCluster         -> {
          changeState(InternalNodeState.FinalizationStart)
        }

        NodeEvent.ShutdownFromUser            -> {
          changeState(InternalNodeState.FinalizationStart)
        }

        NodeEvent.ShutdownFinishedFromCluster -> {
          throw IllegalStateException("not supported")
        }
      }
    }

    /**
     * @return [List]`<MessageT>` node [InternalNodeState] == [InternalNodeState.Terminated]
     * @throws IllegalUnacknowledgedMessagesGetAttempt if [InternalNodeState] !=
     * [InternalNodeState.Terminated]
     * */
    fun getUnacknowledgedMessages(
      node: Node<MessageT, InboundMessageTranslator, Payload>
    ): List<MessageT> {
      if(currState.get() != InternalNodeState.Terminated) {
        throw IllegalUnacknowledgedMessagesGetAttempt()
      }
      return synchronized(node.msgForAcknowledge) {
        node.msgForAcknowledge.keys.toList();
      }
    }

    private fun onBooting(
      node: Node<MessageT, InboundMessageTranslator, Payload>
    ): Long {
      synchronized(node.msgForAcknowledge) {
        if(currState.get() != InternalNodeState.Booting) return 0

        val now = System.currentTimeMillis()
        if(now - node.lastReceive < node.thresholdDelay) {
          currState.set(InternalNodeState.Terminated)
          return 0
        }
        node.sendPingIfNecessary(node.resendDelay, now)

        return node.resendDelay
      }
    }

    private fun onProcessing(
      node: Node<MessageT, InboundMessageTranslator, Payload>
    ): Long {
      synchronized(node.msgForAcknowledge) {
        if(currState.get() != InternalNodeState.Processing) return 0

        val now = System.currentTimeMillis()
        if(now - node.lastReceive > node.thresholdDelay) {
          currState.set(InternalNodeState.Terminated)
          return 0
        }

        val nextDelay = node.checkMessages()
        node.sendPingIfNecessary(nextDelay, now)

        return nextDelay
      }
    }

    private fun onFinalizingProcess(
      node: Node<MessageT, InboundMessageTranslator, Payload>
    ): Long {
      synchronized(node.msgForAcknowledge) {
        if(currState.get() != InternalNodeState.FinalizingProcess) return 0
        val now = System.currentTimeMillis()
        val ret = checkNodeConditions(now, node)
        if(node.msgForAcknowledge.isEmpty()) {
          changeState(InternalNodeState.FinalizedState)
          return 0
        }
        return ret;
      }
    }

    private fun checkNodeConditions(
      now: Long, node: Node<MessageT, InboundMessageTranslator, Payload>
    ): Long {
      if(now - node.lastReceive > node.thresholdDelay) {
        currState.set(InternalNodeState.Terminated)
        return 0
      }
      val nextDelay = node.checkMessages()
      return nextDelay
    }
  }
}