package d.zhdanov.ccfit.nsu.core.network.core.node.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeHandlerAlreadyInitialized
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.NodeContext
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.StateHolder
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

private val Logger = KotlinLogging.logger(ClusterNodesHolder::class.java.name)

class ClusterNodesHolder(
  stateDelayMs: Int,
  private val stateHolder: StateHolder,
) : NodeContext<ClusterNodeT<Node.MsgInfo>>,
    Iterable<Map.Entry<InetSocketAddress, ClusterNodeT<Node.MsgInfo>>> {
  var stateDelayMs: Int = stateDelayMs
    set(value) {
      resendDelay = getResendDelay(value).toLong()
      thresholdDelay = getThresholdDelay(value).toLong()
      field = value
    }
  
  @Volatile var resendDelay = getResendDelay(stateDelayMs).toLong()
    private set
  @Volatile var thresholdDelay = getThresholdDelay(stateDelayMs).toLong()
    private set
  
  override val launched: Boolean
    get() = nodesScope?.isActive ?: false
  @Volatile private var nodesScope: CoroutineScope? = null
  private val nodesByIp =
    ConcurrentHashMap<InetSocketAddress, ClusterNodeT<Node.MsgInfo>>()
  override val nextSeqNum
    get() = stateHolder.nextSeqNum
  
  /**
   * @throws IllegalNodeHandlerAlreadyInitialized
   * */
  @Synchronized
  override fun launch() {
    this.nodesScope ?: throw IllegalNodeHandlerAlreadyInitialized()
    CoroutineScope(Dispatchers.IO).also { nodesScope = it }
  }
  
  @Synchronized
  override fun shutdown() {
    nodesScope?.cancel()
    nodesByIp.clear()
  }
  
  override fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  ) = stateHolder.sendUnicast(msg, nodeAddress)
  
  override fun registerNode(node: ClusterNodeT<Node.MsgInfo>): ClusterNodeT<Node.MsgInfo> {
    val ret = nodesByIp.putIfAbsent(node.ipAddress, node)
    if(ret == null) {
      with(node) {
        nodesScope?.startObservation()
          ?: throw IllegalNodeRegisterAttempt("nodesScope absent")
      }
      return node
    }
    return ret
//    throw IllegalNodeRegisterAttempt("node already registered")
  }
  
  override suspend fun handleNodeTermination(
    node: ClusterNodeT<Node.MsgInfo>
  ) {
    nodesByIp.remove(node.ipAddress)
    stateHolder.handleDeadNode(node)
  }
  
  override suspend fun handleNodeDetach(
    node: ClusterNodeT<Node.MsgInfo>
  ) {
    stateHolder.handleDetachedNode(node)
  }
  
  override fun iterator(): Iterator<Map.Entry<InetSocketAddress, ClusterNodeT<Node.MsgInfo>>> {
    return nodesByIp.entries.iterator()
  }
  
  override operator fun get(ipAddress: InetSocketAddress): ClusterNodeT<Node.MsgInfo>? {
    return nodesByIp[ipAddress]
  }
  
  companion object LobbyStateDelayProvider {
    private const val MAX_THRESHOLD_COEF = 0.8
    private const val MAX_RESEND_DELAY_COEF = 0.1
    fun getResendDelay(stateDelay: Int): Double {
      return stateDelay * MAX_RESEND_DELAY_COEF
    }
    
    fun getThresholdDelay(stateDelay: Int): Double {
      return stateDelay * MAX_THRESHOLD_COEF
    }
  }
}