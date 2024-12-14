package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.ActiveObserverContext
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.LocalNode
import d.zhdanov.ccfit.nsu.core.network.core.states.abstr.AbstractStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.states.abstr.NodeState
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val Logger = KotlinLogging.logger { StateHolder::class.java }
private const val kChannelSize = 10

class StateHolder(
  val nodesHolder: ClusterNodesHandler,
) : AbstractStateHolder {
  @Volatile var localNode: LocalNode = TODO()
  
  private val gameStateHolder = AtomicReference<SnakesProto.GameState?>()
  override val gameState: SnakesProto.GameState?
    get() = gameStateHolder.get()
  
  private val networkStateHolder: AtomicReference<NodeState> = AtomicReference(
    LobbyState(TODO(), TODO(), TODO())
  )
  override val networkState: NodeState
    get() = networkStateHolder.get()
  
  override val masterDeputy: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?
    get() = masterDeputyHolder.get()
  private val masterDeputyHolder: AtomicReference<Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>> =
    AtomicReference()
  
  private val seqNumProvider = AtomicLong(0)
  val nextSeqNum
    get() = seqNumProvider.incrementAndGet()
  
  private val nextNodeIdProvider = AtomicInteger(0)
  val nextNodeId
    get() = nextNodeIdProvider.incrementAndGet()
  
  
  suspend fun handleContextEvent(event: ContextEvent) {
    contextEventChannel.send(event)
  }
  
  suspend fun handleDeadNode(node: ClusterNodeT<Node.MsgInfo>) {
    deadNodeChannel.send(node)
  }
  
  suspend fun handleDetachedNode(node: ClusterNodeT<Node.MsgInfo>) {
    detachNodeChannel.send(node)
  }
  
  private fun handleNodeDetach(node: ClusterNodeT<Node.MsgInfo>) {
    val (msInfo, dpInfo) = masterDeputy!!
    /**
     * Вообще тк у нас исполнение в подобных функциях линейно то нужно
     * использовать !! тк оно не будет занулено
     */
    
    /**вообщем у нас в нодах есть наша локальная
     * нода и она может попасть в ноды которые
     * мы detach поэтому есть доп чеки*/
    if(node.nodeId == msInfo.second) {
      atMasterDetached(node, msInfo, dpInfo)
    } else if(localNode.nodeId == msInfo.second && node.nodeId == dpInfo?.second) {
      atDeputyDetached(node, msInfo, dpInfo)
    } else if(node.nodeId == msInfo.second && localNode.nodeId == dpInfo?.second) {
      atMasterDetachedByDeputy(node, msInfo, dpInfo)
    }
    networkState.atNodeDetach(node)
  }
  
  private fun findNewDeputy(oldDeputy: ClusterNodeT<Node.MsgInfo>?): Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?> {
    val (ms, dp) = this.masterDeputy!!
    /**
     * TODO нужно подумать про чек на мастер
     *  по идее у него уже не будет [ActiveObserverContext] и мы его просто
     *  не выберем
     */
    val newDp = nodesHolder.find {
      it.value.nodeId != ms.second && it.value.payload is ActiveObserverContext && it.value.nodeId != oldDeputy?.nodeId
    }?.value
    val newDpInfo = newDp?.let { Pair(it.ipAddress, it.nodeId) }
    return ms to newDpInfo
  }
  
  private val contextEventChannel: Channel<ContextEvent> = Channel(kChannelSize)
  private val deadNodeChannel: Channel<ClusterNodeT<Node.MsgInfo>> =
    Channel(kChannelSize)
  private val detachNodeChannel: Channel<ClusterNodeT<Node.MsgInfo>> =
    Channel(kChannelSize)
  
  
  /**
   * вообще не трогать в иных местах кроме как nodesNonLobbyWatcherRoutine,
   * потому что это костыль чтобы не было гонки данных изза кривого доступа к
   * функциям которые меняют состояние
   * */
  private val changeToken = Any()
  private fun CoroutineScope.clusterObserverActor() = launch {
    try {
      Logger.info { "${StateHolder::class.java} routine launched" }
      while(isActive) {
        try {
          select {
            detachNodeChannel.onReceive {
            
            }
            deadNodeChannel.onReceive {
            
            }
          }
        }
      }
    } catch(e: IllegalChangeStateAttempt) {
      Logger.error(e) { "${StateHolder::class.java} routine catch" }
    } finally {
      Logger.info { "${StateHolder::class.java} routine finished" }
    }
  }
  
  private fun atMasterDetached(
    msNode: ClusterNodeT<Node.MsgInfo>,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>?,
  ) {
    if(dpInfo != null) {
      masterDeputyHolder.set(dpInfo to null)
      
      val newMsNode = ClusterNode(
        nodeState = Node.NodeState.Passive,
        nodeId = dpInfo.second,
        ipAddress = dpInfo.first,
        clusterNodesHandler = nodesHolder,
      ).apply(nodesHolder::registerNode)
      
      msNode.getUnacknowledgedMessages().map { (ms, _) ->
        MessageUtils.MessageProducer.getMessageForNewMaster(
          ms, localNode.nodeId, dpInfo.second
        )
      }.apply {
        forEach(newMsNode::sendToNode)
        newMsNode.addAllMessageForAck(this)
      }
      TODO("нужно сделать чек не наша ли это нода")
    } else {
      networkState.toLobby(Event.State.ByController.SwitchToLobby, TODO())
    }
  }
  
  private fun atDeputyDetached(
    node: ClusterNodeT<Node.MsgInfo>,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>,
  ) {
    val newMsDpInfo = findNewDeputy(node)
    val (_, newDepInfo) = newMsDpInfo
    masterDeputyHolder.set(newMsDpInfo)
    newDepInfo?.let {
      /**
       * может быть такая ситуация что у нас депути тоже отключитлся во время
       * исполнения сего обряда, тогда когда он придет сюда то перевыберется
       * новый депути и так до бесконечности пока будут кандидаты
       * */
      nodesHolder[it.first]?.apply {
        val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
          msgSeq = nextSeqNum,
          senderId = msInfo.second,
          receiverId = newDepInfo.second,
          receiverRole = SnakesProto.NodeRole.DEPUTY
        )
        sendToNode(msg)
        addMessageForAck(msg)
      }
    }
  }
  
  private fun atMasterDetachedByDeputy(
    node: ClusterNodeT<Node.MsgInfo>,
    msInfo: Pair<InetSocketAddress, Int>,
    dpInfo: Pair<InetSocketAddress, Int>,
  ) {
    val gameState = gameState
    val netState = networkState
    if(gameState == null) {
      Logger.info { "state is null" }
      netState.toLobby(Event.State.ByController.SwitchToLobby, changeToken)
    } else if(netState is ActiveState) {
      netState.toMaster(changeToken, gameState)
//      val oldMsNewDpInfo = findNewDeputy(node)
//      val (_, newDepInfo) = oldMsNewDpInfo
      masterDeputyHolder.set(
        (localNode.ipAddress to localNode.nodeId) to newDepInfo
      )
      newDepInfo?.let {
        val newDepNode = ClusterNode()
        val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
          msgSeq = nextSeqNum,
          senderId = msInfo.second,
          receiverId = newDepInfo.second,
          receiverRole = SnakesProto.NodeRole.DEPUTY
        )
        sendToNode(msg)
        addMessageForAck(msg)
      }
    } else {
      throw IllegalChangeStateAttempt(
        ActiveState::class.java.name,
        netState::class.java.name,
      )
    }
  }
}