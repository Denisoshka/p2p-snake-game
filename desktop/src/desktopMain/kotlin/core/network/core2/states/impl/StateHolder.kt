package d.zhdanov.ccfit.nsu.core.network.core2.states.impl

import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.network.core2.states.StateHolder
import d.zhdanov.ccfit.nsu.core.network.node.connected.ContextEvent
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val Logger = KotlinLogging.logger { StateHolderImpl::class.java }

class StateHolderImpl(
  val nodesHolder: ClusterNodesHolder,
  eventsBackLog: Int = 5,
  deadNodesBackLog: Int = 3,
  switchToPassiveBackLog: Int = 3,
) : StateHolder {
  private val contextEventChannel: Channel<ContextEvent> =
    Channel(capacity = eventsBackLog)
  private val deadNodeChannel: Channel<ClusterNode> =
    Channel(capacity = deadNodesBackLog)
  private val toPassiveNodeChannel: Channel<ClusterNode> =
    Channel(capacity = switchToPassiveBackLog)
  private val seqNumProvider = AtomicLong(0)
  
  override val masterDeputy: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?
    get() = masterDeputyHolder.get()
  private val masterDeputyHolder: AtomicReference<Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>> =
    AtomicReference()
  override val nextSeqNum
    get() = seqNumProvider.incrementAndGet()
  
  override suspend fun handleNodeTermination(node: ClusterNode) {
    deadNodeChannel.send(node)
  }
  
  override suspend fun handleNodeSwitchToPassive(node: ClusterNode) {
    toPassiveNodeChannel.send(node)
  }
  
  override fun findNewDeputy(oldDeputy: ClusterNode): ClusterNode? {
    TODO("Not yet implemented")
  }
  
  private fun CoroutineScope.clusterObserverActor() = launch {
    try {
      Logger.info { "${StateHolderImpl::class.java} routine launched" }
      while(isActive) {
        try {
          select {
            toPassiveNodeChannel.onReceive { node ->
              Logger.trace { "$node detached" }
              handleNodeDetach(node, accessToken)
            }
            deadNodeChannel.onReceive { node ->
              Logger.trace { "$node deactivated" }
              handleNodeDetach(node, accessToken)
            }
            contextEventChannel.onReceive { event ->
              Logger.trace { "$event received" }
            }
          }
        } catch(e: Exception) {
          Logger.error(e) { }
        }
      }
    } catch(e:) {
      Logger.error(e) { "${} routine catch" }
    } finally {
      Logger.info { "${core} routine finished" }
    }
  }
  
  private fun handleNodeDetach(
    node: ClusterNodeT<Node.MsgInfo>, accessToken: Any
  ) {
    val (msInfo, dpInfo) = masterDeputy!!
    
    /**
     * Вообще тк у нас исполнение в подобных функциях линейно то нужно
     * использовать !! так как оно не будет занулено
     *
     * Чисто напоминание для себя что мы не можем быть одновременно deputy и
     * master
     */
    if(node.nodeId == msInfo.second) {
      atMasterDetached(node, msInfo, dpInfo, accessToken)
    } else if(localNode.nodeId == msInfo.second && node.nodeId == dpInfo?.second) {
      atDeputyDetached(node, msInfo, dpInfo)
    } else if(node.nodeId == msInfo.second && localNode.nodeId == dpInfo?.second) {
      atMasterDetachedByDeputy(node, msInfo, dpInfo)
    }
    networkState.atNodeDetachPostProcess(
      node, msInfo, dpInfo, accessToken
    )?.let { stateHolder.set(it) }
  }
  
  private fun atMasterDetached(
    msNode: ClusterNodeT<Node.MsgInfo>,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>?,
    accessToken: Any,
  ) {
    dpInfo ?: return
    masterDeputyHolder.set(dpInfo to null)
    nodesHolder.registerNode(dpInfo.first, dpInfo.second).apply {
    
    }
    val newMsNode =
      d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode(
        nodeId = dpInfo.second,
      ).apply(nodesHolder::registerNode)
    
    msNode.getUnacknowledgedMessages().map { (ms, _) ->
      MessageUtils.MessageProducer.getMessageForNewMaster(
        ms, localNode.nodeId, dpInfo.second
      )
    }.apply {
      forEach(newMsNode::sendToNode)
      newMsNode.addAllMessageForAck(this)
    }
    
  }
}