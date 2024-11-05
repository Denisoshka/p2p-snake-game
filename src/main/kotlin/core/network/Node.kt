package d.zhdanov.ccfit.nsu.core.network

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.exceptions.IllegalUnacknowledgedMessagesGetAttempt
import d.zhdanov.ccfit.nsu.core.network.utils.MessageTranslatorT
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
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
 * @param nodeStateCheckerContext Coroutine context for checking the node state. Default is [Dispatchers.IO].
 */
class Node<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
  initMsgSeqNum: Long,
  messageComparator: Comparator<MessageT>,
  nodeStateCheckerContext: CoroutineContext,
  var nodeRole: NodeRole,
  val address: InetSocketAddress,
  private val resendDelay: Long,
  private val thresholdDelay: Long,
  private val context: NodesHolder<MessageT, InboundMessageTranslator>
) {
  enum class NodeState {
    Runnable, Running, WaitTermination, Terminated,
  }

  @Volatile
  var nodeState: NodeState = NodeState.Running
    private set
  private val msgSeqNum: AtomicLong = AtomicLong(initMsgSeqNum)
  private val observationJob: Job
  private var initMsg: MessageT? = null

  /**
   * Change this values within the scope of synchronized([msgForAcknowledge]).
   */
  private val msgForAcknowledge: TreeMap<MessageT, Long> =
    TreeMap(messageComparator)
  private val lastReceive = AtomicLong()
  private val lastSend = AtomicLong()

  init {
    val delayCoef = 0.8
    observationJob = CoroutineScope(nodeStateCheckerContext).launch {
      try {
        while (true) {
          when (nodeState) {
            NodeState.Runnable -> waitRegistration()
            NodeState.Running -> checkRunningCondition(delayCoef)
            NodeState.WaitTermination -> waitTermination(delayCoef)
            NodeState.Terminated -> { /*xyi =)*/
            }
          }
        }
      } catch (_: CancellationException) {
      } finally {
        context.removeNode(this@Node)
      }
    }
  }

  fun approveMessage(message: MessageT) {
    synchronized(msgForAcknowledge) {
      msgForAcknowledge.remove(message)
      lastReceive.set(System.currentTimeMillis())
    }
  }

  /**
   * @return `false` if node node state!=[NodeState.Running]
   */
  fun addMessageForAcknowledge(message: MessageT): Boolean {
    if (nodeState != NodeState.Running) return false
    synchronized(msgForAcknowledge) {
      msgForAcknowledge[message] = System.currentTimeMillis()
      lastSend.set(System.currentTimeMillis())
    }
    return true
  }

  fun getNextMSGSeqNum(): Long {
    return msgSeqNum.incrementAndGet()
  }

  fun getUnacknowledgedMessages(): List<MessageT> {
    synchronized(msgForAcknowledge) {
      if (nodeState != NodeState.Terminated) {
        throw IllegalUnacknowledgedMessagesGetAttempt(nodeState)
      }
      return msgForAcknowledge.keys.toList();
    }
  }

  private suspend fun waitRegistration() {
    while (nodeState == NodeState.Runnable) {
      val ret = synchronized(msgForAcknowledge) {
        if (nodeState != NodeState.Runnable) return
        if (initMsg != null) {
          if (msgForAcknowledge[initMsg] == null) {
            nodeState = NodeState.Running
            /**
             * Получили Ack на это сообщение и теперь мы зарегистрировались
             * в кластере
             * */
            return
          }
          initMsg
        } else {
          null
        }
      }
      if (ret == null) {
        val ping = context.messageUtils.getPingMsg(msgSeqNum.incrementAndGet())
        context.retrySendMessage(ping, this)
      } else {
        context.retrySendMessage(ret, this)
      }
      delay(resendDelay)
    }
  }

  private suspend fun checkRunningCondition(delayCoef: Double) {
    while (nodeState == NodeState.Running) {
      context.newNodeRegister.send(this@Node)
      val delay = synchronized(msgForAcknowledge) {
        if (nodeState != NodeState.Running) return

        val curTime = System.currentTimeMillis()
        if (curTime - lastReceive.get() > thresholdDelay) {
          nodeState = NodeState.Terminated
          return
        }

        if (msgForAcknowledge.isEmpty()) {
          val ret = if (curTime - lastSend.get() >= resendDelay) {
            val ping = context.messageUtils.getPingMsg(
              msgSeqNum.incrementAndGet()
            )
            addMessageForAcknowledge(ping)
            context.retrySendMessage(ping, this)
            resendDelay
          } else {
            lastReceive.get() + resendDelay - curTime
          }
          return@synchronized ret
        }

        val recheckDelay = resendIfNecessary(curTime)
        lastSend.set(curTime)
        return@synchronized recheckDelay
      }
      delay((delay * delayCoef).toLong())
    }
  }

  private suspend fun waitTermination(delayCoef: Double) {
    while (nodeState == NodeState.WaitTermination) {
      val delay = synchronized(msgForAcknowledge) {
        if (nodeState != NodeState.WaitTermination) return

        val curTime = System.currentTimeMillis()
        if (curTime - lastReceive.get() > thresholdDelay
          || msgForAcknowledge.isEmpty()
        ) {
          nodeState = NodeState.Terminated
          return@synchronized 0;
        }

        val recheckDelay = resendIfNecessary(curTime)
        lastSend.set(curTime)
        return@synchronized recheckDelay
      }
      delay((delayCoef * delay).toLong())
    }
  }

  private fun resendIfNecessary(curTime: Long): Long {
    /**
     * Как обрабатывать то когда нода стала главной, но все еще ждет
     * подтверждения от мастера - мастер отвечает за отправленные сообщения,
     * локальная нода не взаимодействует с другими
     *
     * Что делать с теми сообщениями которые пошли до мастера, но
     * мастер умер - [getUnacknowledgedMessages]
     *
     * вообщем те сообщения которые не получили подтверждение
     * в течении [thresholdDelay] - удаляются, нас в целом не волнует какой
     * последний стейт получил игрок ибо в случае его смерти мы просто
     * переведем его в зрителя и похуй
     */
    val it = msgForAcknowledge.entries.iterator()
    var recheckDelay = resendDelay
    while (it.hasNext()) {
      val msgInfo = it.next()
      val diff = curTime - msgInfo.value
      recheckDelay = recheckDelay.coerceAtLeast(diff)
      if (curTime - msgInfo.value < thresholdDelay) {
        msgInfo.setValue(curTime)
        context.retrySendMessage(msgInfo.key, this)
      } else {
        it.remove()
      }
    }
    return recheckDelay
  }

  fun nodeRegistered(initMsg: MessageT) {
    synchronized(msgForAcknowledge) {
      if (nodeState == NodeState.Runnable) {
        this.initMsg = initMsg
        msgForAcknowledge[initMsg] = System.currentTimeMillis()
        lastSend.set(System.currentTimeMillis())
      } else {
        throw RuntimeException("Node is already initialized!")
      }
    }
  }

  fun shutdown() {
    synchronized(msgForAcknowledge) {
      if (nodeState < NodeState.WaitTermination) {
        nodeState = NodeState.WaitTermination
      }
    }
  }

  fun shutdownNow() {
    synchronized(msgForAcknowledge) {
      nodeState = NodeState.Terminated
    }
  }
}