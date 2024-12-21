package d.zhdanov.ccfit.nsu.core.network.core2.states.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core2.connection.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.network.core2.states.NodeState
import d.zhdanov.ccfit.nsu.core.network.core2.states.StateHolder
import d.zhdanov.ccfit.nsu.core.network.core2.utils.Utils
import d.zhdanov.ccfit.nsu.core.network.node.connected.ContextEvent
import d.zhdanov.ccfit.nsu.core.network.core2.states.impl.state.LobbyStateImpl
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val Logger = KotlinLogging.logger { StateHolderImpl::class.java }

class StateHolderImpl(
  override val nodesHolder: ClusterNodesHolder,
  override val gameController: GameController,
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
  private val gameStateHolder = AtomicReference<SnakesProto.GameState?>()
  private val stateHolder: AtomicReference<NodeState> = AtomicReference(
    LobbyStateImpl(TODO(), TODO(), TODO())
  )
  private val masterDeputyHolder: AtomicReference<Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>> =
    AtomicReference()
  
  override val networkState: NodeState
    get() = stateHolder.get()
  override val gameState: SnakesProto.GameState?
    get() = gameStateHolder.get()
  override val masterDeputy: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?
    get() = masterDeputyHolder.get()
  override val nextSeqNum
    get() = seqNumProvider.incrementAndGet()
  
  override suspend fun handleNodeTermination(node: ClusterNode) {
    deadNodeChannel.send(node)
  }
  
  override suspend fun handleNodeSwitchToPassive(node: ClusterNode) {
    toPassiveNodeChannel.send(node)
  }
  
  override fun findNewDeputy(oldDeputy: ClusterNode): ClusterNode? {
    contextLock.withLock {
      val msdp = this.masterDeputy!!
      val (ms, dp) = msdp
      
      if(dp != null && dp.second != oldDeputy.nodeId) {
      } else {
      
      }
    }
    TODO()
  }
  
  private fun internalFindNewDeputyInfo(
    ms: Pair<InetSocketAddress, Int>, oldDp: Pair<InetSocketAddress, Int>?
  ): Pair<InetSocketAddress, Int>? {
    val newDp = nodesHolder.firstOrNull { (_, node) ->
      node.nodeId != ms.second && node.nodeState == ClusterNode.NodeState.Active && oldDp?.second != node.nodeId
    }?.value
    val newDpInfo = newDp?.let { Pair(it.ipAddress, it.nodeId) }
    return newDpInfo
  }
  
  
  /**
   * вообщем хоть он и юзается в корутине, то есть он пинит поток, но
   * корутина использована чисто для удобства, поэтому не критично
   * */
  private val contextLock = ReentrantLock()
  private val contextExecutor =
    Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  
  private fun CoroutineScope.clusterObserverActor() = launch {
    try {
      Logger.info { "${StateHolderImpl::class.java} routine launched" }
      while(isActive) {
        try {
          select {
            toPassiveNodeChannel.onReceive { node ->
              Logger.trace { "$node detached" }
//
              contextLock.withLock {
                handleNodeDetach(node)
              }
            }
            deadNodeChannel.onReceive { node ->
              Logger.trace { "$node deactivated" }
              contextLock.withLock {
                handleNodeDetach(node)
              }
            }
            contextEventChannel.onReceive { event ->
              Logger.trace { "$event received" }
            }
          }
        } catch(e: Exception) {
          Logger.error(e) { }
        }
      }
    } catch(e: Exception) {
      Logger.error(e) { "${this@StateHolderImpl} routine catch" }
    } finally {
      Logger.info { "${this@StateHolderImpl} routine finished" }
    }
  }
  
  private fun handleNodeDetach(node: ClusterNode) {
    val (msInfo, dpInfo) = masterDeputy!!
    val localNode = nodesHolder.localNode
    /**
     * Вообще тк у нас исполнение в подобных функциях линейно то нужно
     * использовать !! так как оно не будет занулено
     *
     * Чисто напоминание для себя что мы не можем быть одновременно deputy и
     * master
     */
    if(node.nodeId == msInfo.second) {
      atMasterDetached(node, msInfo, dpInfo, localNode)
    } else if(localNode.nodeId == msInfo.second && node.nodeId == dpInfo?.second) {
      atDeputyDetached(node, msInfo, dpInfo)
    } else if(node.nodeId == msInfo.second && localNode.nodeId == dpInfo?.second) {
      atMasterDetachedByDeputy(node, msInfo, dpInfo, localNode)
    }
    networkState.atNodeDetachPostProcess(
      node, msInfo, dpInfo
    )?.let { stateHolder.set(it) }
  }
  
  private fun atMasterDetached(
    msNode: ClusterNode,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>?,
    localNode: ClusterNode
  ) {
    dpInfo ?: return
    masterDeputyHolder.set(dpInfo to null)
    nodesHolder.registerNode(dpInfo.first, dpInfo.second).let {
      msNode.getUnacknowledgedMessages().map { msg ->
        MessageUtils.MessageProducer.getMessageForNewMaster(
          msg, localNode.nodeId, dpInfo.second
        )
      }.let { msgs ->
        msgs.forEach(it::sendToNode)
        it.addAllMessageForAck(msgs)
      }
    }
  }
  
  private fun atDeputyDetached(
    node: ClusterNode,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>,
  ) {
    val newDepInfo = internalFindNewDeputyInfo(msInfo, dpInfo)
    val newMsDpInfo = msInfo to newDepInfo
    masterDeputyHolder.set(newMsDpInfo)
    newDepInfo?.let {
      /**
       *  Может быть такая ситуация, что у нас депути тоже отключитлся во
       *  время исполнения сего обряда, тогда когда он придет сюда то
       *  перевыберется новый депути и так до бесконечности пока будут
       *  кандидаты
       * */
      nodesHolder.registerNode(it.first, it.second).apply {
        val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
          msgSeq = nextSeqNum,
          senderId = msInfo.second,
          receiverId = newDepInfo.second,
          receiverRole = NodeRole.DEPUTY
        )
        sendToNodeWithAck(msg)
      }
    }
  }
  
  private fun atMasterDetachedByDeputy(
    node: ClusterNode,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>,
    localNode: ClusterNode
  ) {
    gameState?.apply {
      val newDep = Utils.findDeputyInState(
        localNode.nodeId, msInfo.second, this
      )
      val newDepInfo = newDep?.let {
        InetSocketAddress(it.ipAddress, it.port) to it.id
      }
      masterDeputyHolder.set(
        (localNode.ipAddress to localNode.nodeId) to newDepInfo
      )
    }
  }
}