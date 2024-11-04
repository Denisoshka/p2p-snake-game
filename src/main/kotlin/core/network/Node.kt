package d.zhdanov.ccfit.nsu.core.network

import d.zhdanov.ccfit.nsu.core.interaction.messages.v1.NodeRole
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
  val nodeId: Int,
  private val resendDelay: Long,
  private val thresholdDelay: Long,
  private val context: P2PContext<MessageT, InboundMessageTranslator>
) : AutoCloseable {
  @Volatile
  var nodeCondition: NodeState = NodeState.Runnable
    private set
  private val msgSeqNum: AtomicLong = AtomicLong(initMsgSeqNum)
  private val observationJob: Job

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
          when (nodeCondition) {
            NodeState.Runnable -> joinState()
            NodeState.Running -> checkConditionState(delayCoef)
            NodeState.Terminated -> {

            }
          }
        }
      } catch (_: CancellationException) {
      } finally {
        context.nodeNotResponding(this@Node)
      }
    }
  }

  fun approveMessage(message: MessageT) {
    synchronized(msgForAcknowledge) {
      msgForAcknowledge.remove(message)
      lastReceive.set(System.currentTimeMillis())
    }
  }

  fun addMessageForAcknowledge(message: MessageT) {
    synchronized(msgForAcknowledge) {
      msgForAcknowledge[message] = System.currentTimeMillis()
      lastSend.set(System.currentTimeMillis())
    }
  }

  fun getNextMSGSeqNum(): Long {
    return msgSeqNum.incrementAndGet()
  }

  fun getUnacknowledgedMessages(): List<MessageT> {
    synchronized(msgForAcknowledge) {
      if (nodeCondition != NodeState.Terminated) {
        throw IllegalUnacknowledgedMessagesGetAttempt(nodeCondition)
      }
      return msgForAcknowledge.keys.toList();
    }
  }

  private fun checkCondition(): Long {
    synchronized(msgForAcknowledge) {
      val curTime = System.currentTimeMillis()
      /**
       * Если мы не получали абсолютно никаких
       * unicast-сообщений от узла в течение
       * 0.8 * state_delay_ms миллисекунд,
       * то мы считаем что узел выпал из игры
       * */
      if (curTime - lastReceive.get() > thresholdDelay) {
        nodeCondition = NodeState.Terminated
        return 0
      }

      if (msgForAcknowledge.isEmpty()) {
        if (curTime - lastSend.get() >= resendDelay) {
          val ping = context.messageUtils.getPingMsg(
            msgSeqNum.incrementAndGet()
          )
          addMessageForAcknowledge(ping)
          context.sendMessage(ping, this)
          return resendDelay
          TODO("fix this")
        } else {
          return lastReceive.get() + resendDelay - curTime
        }
      }

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
      var recheckDelay = Long.MAX_VALUE
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
  }

  private suspend fun joinState() {
    while (nodeCondition == NodeState.Runnable) {
      val toDelay = checkJoin()
      delay(toDelay)
    }
  }

  private fun checkJoin(): Long {

  }

  private suspend fun checkConditionState(delayCoef: Double) {
    context.newNodeRegister.send(this@Node)
    nodeCondition = NodeState.Running
    while (nodeCondition == NodeState.Running) {
      val delay = checkCondition()
      delay((delay * delayCoef).toLong())
    }
  }

  override fun close() {
    nodeCondition = NodeState.Terminated
  }
}