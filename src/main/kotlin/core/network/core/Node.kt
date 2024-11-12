package d.zhdanov.ccfit.nsu.core.network.core

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
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
private val

/**
 * todo fix doc
 * ContextNode class represents a node in a peer-to-peer context.
 *
 * Each node is responsible for monitoring the delivery of sent messages.
 * A message is resent if the [resendDelay] is exceeded.
 * A node is considered dead if the message delay exceeds [thresholdDelay].
 */
class Node<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
  initMsgSeqNum: Long,
  messageComparator: Comparator<MessageT>,
  observerScope: CoroutineScope,
  @Volatile override var nodeRole: NodeRole,
  override val id: Int,
  override val address: InetSocketAddress,
  private val resendDelay: Long,
  private val thresholdDelay: Long,
  private val nodesHandler: NodesHandler<MessageT, InboundMessageTranslator, Payload>
) : NodeT<InetSocketAddress> {
  private val state =
    StateHolder<MessageT, InboundMessageTranslator, Payload>(TODO())
  private val msgSeqNum: AtomicLong = AtomicLong(initMsgSeqNum)

  /**
   * Change this values within the scope of synchronized([msgForAcknowledge]).
   */
  private val msgForAcknowledge: TreeMap<MessageT, Long> =
    TreeMap(messageComparator)
  @Volatile private var lastReceive = 0L
  @Volatile private var lastSend = 0L

  private fun sendPingIfNecessary(nextDelay: Long, now: Long) {
    if(!(nextDelay == resendDelay && now - lastSend >= resendDelay)) return

    val seq = getNextMSGSeqNum()

    val ping = nodesHandler.msgUtils.getPingMsg(seq)
    nodesHandler.sendUnicast(ping, this)
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

  fun addAllMessageForAcknowledge(messages: List<MessageT>): Boolean {
    synchronized(msgForAcknowledge) {
      for(msg in messages) {
        msgForAcknowledge[msg] = System.currentTimeMillis()
      }
      lastSend = System.currentTimeMillis()
    }
    return true
  }

  override fun handleEvent(event: NodeEvent) = state.onEvent(event)

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

  fun getUnacknowledgedMessages(): List<MessageT> {
    return state.getUnacknowledgedMessages(this)
  }

  fun getNextMSGSeqNum(): Long {
    return msgSeqNum.incrementAndGet()
  }

  private class StateHolder<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
    state: InternalNodeState
  ) {
    private val statemachineState = AtomicReference(state)
    private var bootChannel: Channel<NodeEvent> = Channel()

    /**
     * Represents the various states of a node in the system.
     */
    enum class InternalNodeState {
      Booting,
      Processing,
      FinalizationStart,
      FinalizingProcess,
      FinalizedState,
      Terminated
    }

    fun CoroutineScope.startObservation(
      node: Node<MessageT, InboundMessageTranslator, Payload>
    ): Job {
      return launch {
        while(true) {
          when(statemachineState.get()) {
            InternalNodeState.Booting           -> {
              val ret = onBooting(node)
              delay(ret)
            }

            InternalNodeState.Processing        -> {
              val ret = onOperational(node)
              delay(ret)
            }

            InternalNodeState.FinalizationStart -> {
              if(changeState(InternalNodeState.FinalizingProcess)) {
                node.nodesHandler.prepareForDetachNode(node)
              }
            }

            InternalNodeState.FinalizingProcess -> {
              val ret = onFinalizingProcess(node)
              delay(ret)
            }

            InternalNodeState.FinalizedState    -> {
              statemachineState.set(InternalNodeState.Terminated)
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
        val prevState = statemachineState.get()
        if(newState <= prevState) return false;
      } while(statemachineState.compareAndSet(prevState, newState))
      return true
    }

    fun onEvent(event: NodeEvent) {
      when(event) {
        NodeEvent.NodeRegistered              -> {
          if(statemachineState.get() != InternalNodeState.Booting) throw IllegalNodeRegisterAttempt()
          bootChannel.run {
            val ret = trySend(event)
            if(ret.isFailure || ret.isClosed) throw IllegalNodeRegisterAttempt()
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
      if(statemachineState.get() != InternalNodeState.Terminated) {
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
        if(statemachineState.get() != InternalNodeState.Booting) return 0

        val now = System.currentTimeMillis()
        if(now - node.lastReceive < node.thresholdDelay) {
          statemachineState.set(InternalNodeState.Terminated)
          return 0
        }
        node.sendPingIfNecessary(node.resendDelay, now)

        return node.resendDelay
      }
    }

    private fun onOperational(
      node: Node<MessageT, InboundMessageTranslator, Payload>
    ): Long {
      synchronized(node.msgForAcknowledge) {
        if(statemachineState.get() != InternalNodeState.Processing) return 0

        val now = System.currentTimeMillis()
        if(now - node.lastReceive > node.thresholdDelay) {
          statemachineState.set(InternalNodeState.Terminated)
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
        if(statemachineState.get() != InternalNodeState.FinalizingProcess) return 0
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
        statemachineState.set(InternalNodeState.Terminated)
        return 0
      }
      val nextDelay = node.checkMessages()
      return nextDelay
    }
  }
}