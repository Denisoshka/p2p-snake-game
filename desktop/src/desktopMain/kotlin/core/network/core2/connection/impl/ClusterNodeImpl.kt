package d.zhdanov.ccfit.nsu.core.network.core2.connection.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

private val Logger = KotlinLogging.logger { ClusterNodeImpl::class.java }

class ClusterNodeImpl(
  nodeRole: ClusterNode.NodeState,
  override val nodeId: Int,
  override val ipAddress: InetSocketAddress,
  override val nodeHolder: ClusterNodesHolder,
) : ClusterNode {
  private val onSwitchToPassive = Channel<ClusterNode.NodeState>(capacity = 1)
  private val onTerminatedHandler = Channel<ClusterNode.NodeState>(capacity = 1)
  private val thresholdDelay = nodeHolder.thresholdDelay
  private val resendDelay = nodeHolder.resendDelay
  override val nodeState: ClusterNode.NodeState
    get() = stateHolder.get()
  override var lastReceive: Long = System.currentTimeMillis()
  override var lastSend: Long = System.currentTimeMillis()
  private val stateHolder = AtomicReference<ClusterNode.NodeState>()
  private val msgForAck: MutableMap<SnakesProto.GameMessage, ClusterNode.MsgInfo> =
    TreeMap(MessageUtils.messageComparator)
  
  init {
    if(nodeRole > ClusterNode.NodeState.Passive) {
      throw IllegalNodeRegisterAttempt("illegal initial node state $nodeRole")
    }
  }
  
  override fun sendToNode(msg: SnakesProto.GameMessage) {
    nodeHolder.sendUnicast(msg, ipAddress)
  }
  
  override fun sendToNodeWithAck(msg: SnakesProto.GameMessage) {
    sendToNode(msg)
    addMessageForAck(msg)
  }
  
  override fun ackMessage(message: SnakesProto.GameMessage): SnakesProto.GameMessage? {
    synchronized(msgForAck) {
      return msgForAck[message]?.req
    }
  }
  
  override fun addMessageForAck(message: SnakesProto.GameMessage) {
    synchronized(msgForAck) {
      msgForAck[message] = ClusterNode.MsgInfo(message, lastReceive)
    }
  }
  
  override fun addAllMessageForAck(messages: Iterable<SnakesProto.GameMessage>) {
    synchronized(msgForAck) {
      messages.forEach { msg ->
        msgForAck[msg] = ClusterNode.MsgInfo(msg, lastReceive)
      }
    }
  }
  
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun CoroutineScope.startObservation(): Job {
    return launch {
      var nextDelay = 0L
      try {
        Logger.trace { "${this@ClusterNodeImpl} startObservation" }
        while(isActive) {
          select<Unit> {
            onSwitchToPassive.onReceive { state ->
              Logger.trace {
                "${this@ClusterNodeImpl} receive switch to $state state"
              }
              onSwitchToPassive.close()
              if(nodeState != ClusterNode.NodeState.Terminated) {
                stateHolder.set(ClusterNode.NodeState.Passive)
                this@ClusterNodeImpl.nodeHolder.apply {
                  handleSwitchToPassive(this@ClusterNodeImpl)
                }
              }
            }
            
            onTerminatedHandler.onReceive { state ->
              Logger.trace {
                "${this@ClusterNodeImpl} receive switch to $state state"
              }
              onTerminatedHandler.close()
              stateHolder.set(ClusterNode.NodeState.Terminated)
              this@ClusterNodeImpl.nodeHolder.apply {
                handleNodeTermination(this@ClusterNodeImpl)
              }
            }
            
            onTimeout(nextDelay) {
              when(nodeState) {
                ClusterNode.NodeState.Active, ClusterNode.NodeState.Passive -> {
                  nextDelay = onProcessing()
                }
                
                ClusterNode.NodeState.Terminated                            -> {
                  /**
                   * по идее сюда вообще никогда не должны попасть
                   */
                }
              }
            }
          }
        }
      } catch(e: CancellationException) {
        this.cancel()
      } finally {
        onSwitchToPassive.close()
        onTerminatedHandler.close()
      }
    }
  }
  
  override fun markAsPassive() {
    onSwitchToPassive.trySend(ClusterNode.NodeState.Passive).onSuccess {
      Logger.trace { "$this mark as passive" }
    }
  }
  
  override fun shutdown() {
    onTerminatedHandler.trySend(ClusterNode.NodeState.Terminated).onSuccess {
      Logger.trace { "$this shutdowned" }
    }
  }
  
  override fun getUnacknowledgedMessages(): List<SnakesProto.GameMessage> {
    synchronized(msgForAck) {
      return msgForAck.keys.toList()
    }
  }
  
  override fun toString(): String {
    return "ClusterNodeImpl(nodeState=$nodeState, nodeHolder=$nodeHolder, ipAddress=$ipAddress, nodeId=$nodeId)"
  }
  
  private fun checkMessages(): Long {
    var ret = resendDelay
    val now = System.currentTimeMillis()
    val it = msgForAck.iterator()
    while(it.hasNext()) {
      val entry = it.next()
      val (msg, msgInfo) = entry
      if(now - msgInfo.lastCheck < thresholdDelay) {
        ret = ret.coerceAtMost(thresholdDelay + msgInfo.lastCheck - now)
        msgInfo.lastCheck = now
        
        sendToNode(msg)
      } else {
        it.remove()
      }
    }
    return ret
  }
  
  private fun onProcessing(): Long {
    synchronized(msgForAck) {
      val now = System.currentTimeMillis()
      val nextDelay = checkNodeConditions(now)
      sendPingIfNecessary(nextDelay, now)
      
      return nextDelay
    }
  }
  
  private fun sendPingIfNecessary(nextDelay: Long, now: Long) {
    if(!(nextDelay == resendDelay && now - lastSend >= resendDelay)) return
    
    val seq = nodeHolder.nextSeqNum
    val ping = MessageUtils.MessageProducer.getPingMsg(seq)
    sendToNodeWithAck(ping)
    
    lastSend = System.currentTimeMillis()
  }
  
  private fun checkNodeConditions(now: Long): Long {
    if(now - lastReceive > thresholdDelay) {
      shutdown()
      return 0
    }
    return checkMessages()
  }
}