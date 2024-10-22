package d.zhdanov.ccfit.nsu.core.network

import d.zhdanov.ccfit.nsu.core.interaction.messages.NodeRole
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
 * @param nodeRole The role of the node within the network topology.
 * @param pingDelay Delay in milliseconds for pinging the node.
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
  @Volatile var nodeRole: NodeRole,
  private val pingDelay: Long,
  private val resendDelay: Long,
  private val thresholdDelay: Long,
  private val context: P2PContext<MessageT, InboundMessageTranslator>
) : AutoCloseable {
  enum class NodeState {
    JoiningCluster, Alive, Dead,
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
  private val lastAction = AtomicLong(System.currentTimeMillis())

  init {
    val delayCoef = 0.8
    observationJob = CoroutineScope(nodeStateCheckerContext).launch {
      try {
        while (nodeState == NodeState.Alive) {
          val delay = checkNodeState()
          delay((delay * delayCoef).toLong())
        }
      } catch (_: CancellationException) {
      }
    }
    //    TODO Нужно ли делать equals для нод
  }

  fun approveMessage(message: MessageT) {
    synchronized(msgForAcknowledge) {
      msgForAcknowledge.remove(message)
      lastAction.set(System.currentTimeMillis())
    }
  }

  fun addMessageForAcknowledge(message: MessageT) {
    synchronized(msgForAcknowledge) {
      msgForAcknowledge[message] = System.currentTimeMillis()
      TODO("что то я сомневаюсь что здесь не нужно учитывать lastAction")
    }
  }

  fun getUnacknowledgedMessages(): List<MessageT> {
    if (nodeState != NodeState.Dead) {
      return listOf()
    }
    synchronized(msgForAcknowledge) {
      return msgForAcknowledge.keys.toList();
    }
  }

  private fun checkNodeState(): Long {
    synchronized(msgForAcknowledge) {
      val curTime = System.currentTimeMillis()
      if (msgForAcknowledge.isEmpty()) {
        if (curTime - lastAction.get() > pingDelay) {
          pingMaster()
        }
        return lastAction.get() + pingDelay - curTime
      }/*
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
      if (curTime - lastAction.get() > thresholdDelay) {
        onNodeDead()
        return 0
      }
      for (msgInfo in msgForAcknowledge) {
        if (curTime - msgInfo.value < resendDelay) {
          break
        } else {
          msgInfo.setValue(curTime)
          TODO("нужно переслать сообщение")
        }
      }
      return entry.value + thresholdDelay - curTime
    }
  }

  private fun pingMaster() {
    if (nodeRole == NodeRole.MASTER) return
    val ping = context.messageUtils.getPingMsg(msgSeqNum.incrementAndGet())
    val masterAddress = context.masterNode.address
    addMessageForAcknowledge(ping, masterAddress)
    context.sendMessage(ping, this)
  }

  private fun onNodeDead() {
    close()
    context.onNodeDead(this)
  }

  override fun close() {
    nodeState = NodeState.Dead
    observationJob.cancel()
  }
}
