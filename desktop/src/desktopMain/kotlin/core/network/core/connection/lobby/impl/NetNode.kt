package d.zhdanov.ccfit.nsu.core.network.core.states.node.lobby.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeContext
import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeT
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

private val Logger = KotlinLogging.logger(NetNode::class.java.name)

class NetNode(
  val context: NodeContext<NetNode>,
  override val nodeId: Int,
  override val ipAddress: InetSocketAddress,
  private val resendDelay: Long,
  private val thresholdDelay: Long,
) : NodeT {
  @Volatile private var observeJob: Job? = null
  @Volatile override var lastReceive = System.currentTimeMillis()
  @Volatile override var lastSend = System.currentTimeMillis()

  @Volatile private var nodeStateHolder: NodeT.NodeState =
    NodeT.NodeState.Passive
  override val nodeState: NodeT.NodeState
    get() = nodeStateHolder
  override val running: Boolean
    get() = nodeStateHolder == NodeT.NodeState.Passive
  private val msgs: TreeMap<SnakesProto.GameMessage, NodeT.MsgInfo> = TreeMap(
    MessageUtils.messageComparator
  )

  override fun sendToNode(msg: SnakesProto.GameMessage) {
    context.sendUnicast(msg, ipAddress)
  }

  private fun checkMessages(): Long {
    var ret = resendDelay
    val now = System.currentTimeMillis()
    val it = msgs.iterator()
    while(it.hasNext()) {
      val entry = it.next()
      val (msg, msgInfo) = entry
      if(now - msgInfo.lastCheck < thresholdDelay) {
        ret = ret.coerceAtMost(thresholdDelay + msgInfo.lastCheck - now)
        entry.value.lastCheck = now
        context.sendUnicast(msg, ipAddress)
      } else {
        it.remove()
      }
    }
    return ret
  }

  override fun ackMessage(message: SnakesProto.GameMessage): SnakesProto.GameMessage? {
    synchronized(msgs) {
      lastReceive = System.currentTimeMillis()
      return msgs.remove(message)?.msg
    }
  }

  override fun addMessageForAck(message: SnakesProto.GameMessage) {
    synchronized(msgs) {
      msgs[message] = NodeT.MsgInfo(
        message, System.currentTimeMillis()
      )
    }
    lastSend = System.currentTimeMillis()
  }

  override fun addAllMessageForAck(messages: List<SnakesProto.GameMessage>) {
    synchronized(msgs) {
      messages.forEach {
        msgs[it] = NodeT.MsgInfo(it, System.currentTimeMillis())
      }
      lastSend = System.currentTimeMillis()
    }
  }

  @Synchronized
  override fun CoroutineScope.startObservation(): Job {
    return launch {
      Logger.info { "${this@NetNode} launched" }
      try {
        while(coroutineContext.isActive) {
          if(!running) break

          val nextDelay = onProcessing()

          delay(nextDelay)
        }
        context.handleNodeTermination(this@NetNode)
      } catch(_: CancellationException) {
        this.cancel()
      } catch(e: Exception) {
        Logger.error(e) { "${this@NetNode} unexpected exception" }
      }
      Logger.info { "${this@NetNode} finished " }
    }.also { observeJob = it }
  }

  private fun onProcessing(): Long {
    synchronized(msgs) {
      val now = System.currentTimeMillis()
      val nextDelay = checkNodeConditions(now)
      sendPingIfNecessary(nextDelay, now)

      return nextDelay
    }
  }

  private fun checkNodeConditions(now: Long): Long {
    if(now - lastReceive > thresholdDelay) {
      nodeStateHolder = NodeT.NodeState.Terminated
      return 0
    }
    return checkMessages()
  }

  private fun sendPingIfNecessary(nextDelay: Long, now: Long) {
    if(!(nextDelay == resendDelay && now - lastSend >= resendDelay)) return

    val seq = context.nextSeqNum
    val ping = MessageUtils.getPingMsg(seq)

    sendToNode(ping)
    lastSend = System.currentTimeMillis()
  }

  fun messageReceived(message: SnakesProto.GameMessage) {
    lastReceive = System.currentTimeMillis()
  }

  override fun detach() {
    Logger.info { "${this@NetNode} detached " }
    nodeStateHolder = NodeT.NodeState.Terminated;
  }

  override fun shutdown() {
    Logger.info { "${this@NetNode} shutdown " }
    NodeT.NodeState.Terminated
  }

  override fun getUnacknowledgedMessages(): List<SnakesProto.GameMessage> {
    synchronized(msgs) {
      return msgs.keys.toList()
    }
  }

  override fun toString(): String {
    return "NetNode(nodeId=$nodeId, ipAddress=$ipAddress)"
  }
}