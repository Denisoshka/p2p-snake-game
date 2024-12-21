package d.zhdanov.ccfit.nsu.core.network.node.connected

import core.network.core.connection.lobby.impl.NetNodeHandler
import d.zhdanov.ccfit.nsu.core.network.core2.utils.Utils
import core.network.node.interfaces.StateHolder
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.LocalNode
import d.zhdanov.ccfit.nsu.core.network.interfaces.StateService
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.UnicastNetHandler
import d.zhdanov.ccfit.nsu.core.network.core2.states.NodeState
import d.zhdanov.ccfit.nsu.core.network.core2.states.impl.state.LobbyStateImpl
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

private val Logger = KotlinLogging.logger { StateHolder::class.java }
private const val kChannelSize = 10
private const val RetryJoinLater = "retry join later"

class StateHolder(
  val gameController: GameController,
  val nodesHolder: ClusterNodesHolder,
  val netNodesHandler: NetNodeHandler,
  private val unicastNetHandler: UnicastNetHandler,
) : StateHolder, StateService {
  @Volatile var localNode: LocalNode = TODO()
  
  private val gameStateHolder = AtomicReference<SnakesProto.GameState?>()
  override val gameState: SnakesProto.GameState?
    get() = gameStateHolder.get()
  
  private val stateHolder: AtomicReference<NodeState> = AtomicReference(
    LobbyStateImpl(TODO(), TODO(), TODO())
  )
  override val networkState: NodeState
    get() = stateHolder.get()
  
  override val masterDeputy: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?
    get() = masterDeputyHolder.get()
  private val masterDeputyHolder: AtomicReference<Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>> =
    AtomicReference()
  
  private val seqNumProvider = AtomicLong(0)
  val nextSeqNum
    get() = seqNumProvider.incrementAndGet()
  
  fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  ) = unicastNetHandler.sendUnicastMessage(msg, nodeAddress)
  
  
  suspend fun handleContextEvent(event: ContextEvent) {
    contextEventChannel.send(event)
  }
  
  suspend fun handleDeadNode(node: ClusterNodeT<Node.MsgInfo>) {
    deadNodeChannel.send(node)
  }
  
  suspend fun handleDetachedNode(node: ClusterNodeT<Node.MsgInfo>) {
    detachNodeChannel.send(node)
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
  
  private fun findNewDeputy(oldDeputy: ClusterNodeT<Node.MsgInfo>?): Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?> {
    val (ms, _) = this.masterDeputy!!
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
   **/
  private val accessToken = Any()
  private fun CoroutineScope.clusterObserverActor() = launch {
    try {
      Logger.info { "${StateHolder::class.java} routine launched" }
      while(isActive) {
        try {
          select {
            detachNodeChannel.onReceive { node ->
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
    accessToken: Any,
  ) {
    if(dpInfo != null) {
      masterDeputyHolder.set(dpInfo to null)
      
      val newMsNode = ClusterNode(
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
       *  Может быть такая ситуация, что у нас депути тоже отключитлся во
       *  время исполнения сего обряда, тогда когда он придет сюда то
       *  перевыберется новый депути и так до бесконечности пока будут
       *  кандидаты
       * */
      nodesHolder[it.first]?.apply {
        val msg = MessageUtils.MessageProducer.getRoleChangeMsg(
          msgSeq = nextSeqNum,
          senderId = msInfo.second,
          receiverId = newDepInfo.second,
          receiverRole = NodeRole.DEPUTY
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
    gameState?.apply {
      val newDep = Utils.findDeputyInState(
        localNode.nodeId, this
      )
      val newDepInfo = newDep?.let {
        InetSocketAddress(it.ipAddress, it.port) to it.id
      }
      masterDeputyHolder.set(
        (localNode.ipAddress to localNode.nodeId) to newDepInfo
      )
    }
  }
  
  override fun submitState(state: SnakesProto.GameState.Builder) {
    val (ms, dp) = masterDeputy ?: return
    for((_, node) in nodesHolder) {
      node.shootContextState(state, ms, dp)
    }
    state.build()
    for((_, node) in nodesHolder) {
      node.
    }
  }
  
  override fun submitNewRegisteredPlayer(
    ipAddr: InetSocketAddress, initMessage: SnakesProto.GameMessage, id: Int?
  ) {
    id ?: return
    val nodeState = when(initMessage.join.requestedRole) {
      SnakesProto.NodeRole.VIEWER -> Node.NodeState.Passive
      else                        -> Node.NodeState.Active
    }
    nodesHolder.registerNode(ipAddr, id)
  }
}