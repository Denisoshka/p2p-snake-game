package d.zhdanov.ccfit.nsu.core.network.core2.connection

import d.zhdanov.ccfit.nsu.SnakesProto
import java.net.InetAddress
import java.net.InetSocketAddress

interface ClusterNodesHolder :
  Iterable<Map.Entry<InetSocketAddress, ClusterNode>> {
  val launched: Boolean
  val resendDelay: Long
  val thresholdDelay: Long
  val nextSeqNum: Long
  fun launch(nodeId: Int, nodeRole: ClusterNode.NodeState): ClusterNode
  fun shutdown()
  fun clear()
  fun registerNode(
    ipAddress: InetSocketAddress, id: Int, nodeRole: ClusterNode.NodeState
  ): ClusterNode
  
  suspend fun handleNodeTermination(node: ClusterNode)
  suspend fun handleSwitchToPassive(node: ClusterNode)
  fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  )
  
  operator fun get(ipAddress: InetSocketAddress): ClusterNode?
  
  companion object LobbyStateDelayProvider {
    private const val MAX_THRESHOLD_COEF = 0.8
    private const val MAX_RESEND_DELAY_COEF = 0.1
    fun getResendDelay(stateDelay: Int): Double {
      return stateDelay * MAX_RESEND_DELAY_COEF
    }
    
    fun getThresholdDelay(stateDelay: Int): Double {
      return stateDelay * MAX_THRESHOLD_COEF
    }
    
    val LocalIpAddress = InetSocketAddress(InetAddress.getLocalHost(), 0)
  }
}