package core.network.core.connection.game.impl

import core.network.core.connection.Node
import core.network.core.connection.NodeContext
import core.network.core.connection.game.ClusterNodeT
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeHandlerAlreadyInitialized
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

private val Logger = KotlinLogging.logger(ClusterNodesHandler::class.java.name)

class ClusterNodesHandler(
  stateDelayMs: Int,
  private val ncStateMachine: NetworkStateHolder,
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
    get() = ncStateMachine.nextSeqNum
  
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
  ) = ncStateMachine.sendUnicast(msg, nodeAddress)
  
  override fun registerNode(node: ClusterNodeT<Node.MsgInfo>): ClusterNode {
    nodesByIp.putIfAbsent(node.ipAddress, node)?.let {
      with(it) {
        nodesScope?.startObservation()
          ?: throw IllegalNodeRegisterAttempt("nodesScope absent")
      }
      return it
    } ?: throw IllegalNodeRegisterAttempt("node already registered")
  }
  
  override suspend fun handleNodeTermination(
    node: ClusterNodeT<Node.MsgInfo>
  ) {
    nodesByIp.remove(node.ipAddress)
    ncStateMachine.terminateNode(node)
  }
  
  override suspend fun handleNodeDetach(
    node: ClusterNodeT<Node.MsgInfo>
  ) {
    ncStateMachine.detachNode(node)
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