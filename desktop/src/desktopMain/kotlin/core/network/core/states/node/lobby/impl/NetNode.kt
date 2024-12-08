package d.zhdanov.ccfit.nsu.core.network.core.states.node.lobby.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeT
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

class NetNode(
  messageComparator: Comparator<SnakesProto.GameMessage>,
  override val nodeId: Int,
  override val ipAddress: InetSocketAddress,
  val context: NetNodeContext,
  private val resendDelay: Long,
  private val thresholdDelay: Long, override val running: Boolean,
) : NodeT {
  @Volatile private var observeJob: Job? = null

  @Volatile override var lastReceive = System.currentTimeMillis()
  @Volatile override var lastSend = System.currentTimeMillis()

  @Volatile private var expired = false

  private val msgs: TreeMap<SnakesProto.GameMessage, NodeT.MsgInfo> = TreeMap(
    messageComparator
  )

  override fun sendToNode(msg: SnakesProto.GameMessage) {
    context.sendUnicast(msg, ipAddress)
  }

  private fun checkNodeConditions(now: Long) {
    if(now - lastReceive > thresholdDelay) {

    }
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
      try {
        while(coroutineContext.isActive) {
          if(expired) break
          val now = System.currentTimeMillis()
          checkNodeConditions(now);
          delay(ret)
        }
      } catch(_: CancellationException) {
      }
    }.also { observeJob = it }
  }

  override fun detach() {
    expired = true;
  }

  fun messageReceived(message: SnakesProto.GameMessage) {
    lastReceive = System.currentTimeMillis()
  }

  @Synchronized
  override fun shutdown() {
    observeJob?.cancel()
  }

  override fun getUnacknowledgedMessages(): List<SnakesProto.GameMessage> {
    synchronized(msgs) {
      return msgs.keys.toList()
    }
  }
}