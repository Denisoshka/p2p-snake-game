package d.zhdanov.ccfit.nsu.core.network

import d.zhdanov.ccfit.nsu.core.network.utils.AbstractMessageTranslator
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
class Node<MessageT, InboundMessageTranslator : AbstractMessageTranslator<MessageT>>(
  initMsgSeqNum: Long,
  messageComparator: Comparator<MessageT>,
  nodeStateCheckerContext: CoroutineContext,
  val address: InetSocketAddress,
  val nodeId: Int,
  private val resendDelay: Long,
  private val thresholdDelay: Long,
  private val context: P2PContext<MessageT, InboundMessageTranslator>
) : AutoCloseable {
  enum class NodeState {
    Alive, Dead,
  }

  @Volatile
  var nodeState: NodeState = NodeState.Alive
    private set

  //  todo может сделать так что когда мы становимся мастером то все сообщения
  //  которые отправили мастеру трем и начинаем собирать все новые?
  /**
   * Change this value within the scope of synchronized([msgForAcknowledge]).
   */
  private val msgForAcknowledge: TreeMap<MessageT, Long> =
    TreeMap(messageComparator)
  private val msgSeqNum: AtomicLong = AtomicLong(initMsgSeqNum)
  private val observationJob: Job

  /**
   * Change this value within the scope of synchronized([msgForAcknowledge]).
   */
  private val lastReceive = AtomicLong(System.currentTimeMillis())
  private val lastSend = AtomicLong(System.currentTimeMillis())

  init {
    val delayCoef = 0.8
    observationJob = CoroutineScope(nodeStateCheckerContext).launch {
      try {
        while (nodeState == NodeState.Alive) {
          val delay = checkActivity()
          delay((delay * delayCoef).toLong())
        }
      } catch (_: CancellationException) {
      } finally {
        context.onNodeDead(this)
      }
    }
    //    TODO Нужно ли делать equals для нод
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
      TODO("что то я сомневаюсь что здесь не нужно учитывать lastAction")
    }
  }

  fun getUnacknowledgedMessages(): List<MessageT> {
    synchronized(msgForAcknowledge) {
      if (nodeState != NodeState.Dead) {
        return listOf()
//      todo может сделать эксепшн
      }
      return msgForAcknowledge.keys.toList();
    }
  }

  private fun checkActivity(): Long {
    synchronized(msgForAcknowledge) {
      val curTime = System.currentTimeMillis()
      if (curTime - lastReceive.get() > thresholdDelay) {
        nodeState = NodeState.Dead
        return 0;
      }
      if (msgForAcknowledge.isEmpty()) {
        if (curTime - lastReceive.get() >= resendDelay) {
          ping()
          return resendDelay
        }
        return lastReceive.get() + resendDelay - curTime
      }
      /*
      TODO нужно что то сделать когда есть те сообщения которые дошли и
      нода подает признаки жизни, а есть старые которые не дошли
      */
      val entry = msgForAcknowledge.firstEntry()
      if (curTime - entry.value < resendDelay) {
        return entry.value + resendDelay - curTime
      }

      /**
       * Как обрабатывать то когда нода стала главной, но все еще ждет
       * подтверждения от мастера - мастер отвечает за отправленные сообщения,
       * локальная нода не взаимодействует с другими
       * Что делать с теми сообщениями которые пошли до мастера, но
       * мастер умер - [getUnacknowledgedMessages]
       */
      val it = msgForAcknowledge.entries.iterator()
      while (it.hasNext()) {
        val msgInfo = it.next()

        if (curTime - msgInfo.value < resendDelay) {
          break
        } else if (curTime - msgInfo.value < thresholdDelay) {
          msgInfo.setValue(curTime)
          TODO("нужно переслать сообщение")
        } else {
          it.remove()
        }
      }
      return entry.value + thresholdDelay - curTime
    }
  }

  private fun ping() {
    val ping = context.messageUtils.getPingMsg(msgSeqNum.incrementAndGet())
    addMessageForAcknowledge(ping)
    context.sendMessage(ping, this)
  }

  override fun close() {
    nodeState = NodeState.Dead
  }
}