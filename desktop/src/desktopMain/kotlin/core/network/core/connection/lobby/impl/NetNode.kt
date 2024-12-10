package core.network.core.connection.lobby.impl

import core.network.core.connection.Node
import core.network.core.connection.NodeContext
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

private val Logger = KotlinLogging.logger(NetNode::class.java.name)

class NetNode(
  val context: NodeContext<NetNode>,
  override val ipAddress: InetSocketAddress,
  private val resendDelay: Long,
  private val thresholdDelay: Long,
) : Node<Node.MsgInfoWithPayload> {
  override val nodeId: Int = 0;
  @Volatile private var observeJob: Job? = null
  @Volatile override var lastReceive = System.currentTimeMillis()
  @Volatile override var lastSend = System.currentTimeMillis()
  
  @Volatile private var nodeStateHolder: Node.NodeState =
    Node.NodeState.Passive
  override val nodeState: Node.NodeState
    get() = nodeStateHolder
  override val running: Boolean
    get() = nodeStateHolder == Node.NodeState.Passive
  private val msgs: TreeMap<SnakesProto.GameMessage, Node.MsgInfoWithPayload> =
    TreeMap(
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
  
  override fun ackMessage(message: SnakesProto.GameMessage): Node.MsgInfoWithPayload? {
    synchronized(msgs) {
      lastReceive = System.currentTimeMillis()
      return msgs.remove(message)
    }
  }
  
  override fun addMessageForAck(message: SnakesProto.GameMessage) {
    synchronized(msgs) {
      msgs[message] = Node.MsgInfoWithPayload(
        message, System.currentTimeMillis(), null
      )
      lastSend = System.currentTimeMillis()
    }
  }
  
  fun addMessageForAck(
    message: SnakesProto.GameMessage, irritant: SnakesProto.GameMessage
  ) {
    synchronized(msgs) {
      msgs[message] = Node.MsgInfoWithPayload(
        message, System.currentTimeMillis(), irritant
      )
      lastSend = System.currentTimeMillis()
    }
  }
  
  override fun addAllMessageForAck(messages: List<Node.MsgInfoWithPayload>) {
    /*synchronized(msgs) {
      messages.forEach {
        msgs[it] = Node.MsgInfo(it, System.currentTimeMillis())
      }
      lastSend = System.currentTimeMillis()
    }*/
    TODO()
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
      nodeStateHolder = Node.NodeState.Terminated
      return 0
    }
    return checkMessages()
  }
  
  private fun sendPingIfNecessary(nextDelay: Long, now: Long) {
    if(!(nextDelay == resendDelay && now - lastSend >= resendDelay)) return
    
    val seq = context.nextSeqNum
    val ping = MessageUtils.MessageProducer.getPingMsg(seq)
    
    sendToNode(ping)
    lastSend = System.currentTimeMillis()
  }
  
  fun messageReceived(message: SnakesProto.GameMessage) {
    lastReceive = System.currentTimeMillis()
  }
  
  override fun detach() {
    Logger.info { "${this@NetNode} detached " }
    nodeStateHolder = Node.NodeState.Terminated;
  }
  
  override fun shutdown() {
    Logger.info { "${this@NetNode} shutdown " }
    Node.NodeState.Terminated
  }
  
  override fun getUnacknowledgedMessages(): List<Node.MsgInfoWithPayload> {
    synchronized(msgs) {
      return msgs.values.toList()
    }
  }
  
  override fun toString(): String {
    return "NetNode(nodeId=$nodeId, ipAddress=$ipAddress)"
  }
}