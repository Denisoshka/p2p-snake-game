package d.zhdanov.ccfit.nsu.core.network.core

import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.LocalNode
import core.network.core.connection.lobby.impl.NetNodeHandler
import core.network.core.states.utils.Utils
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.GameController
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.abstr.LobbyStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.abstr.NodeState
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.ActiveState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.LobbyState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.MasterState
import d.zhdanov.ccfit.nsu.core.network.core.states.impl.PassiveState
import d.zhdanov.ccfit.nsu.core.network.interfaces.GameSessionHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkStateContext
import d.zhdanov.ccfit.nsu.core.network.interfaces.StateConsumer
import d.zhdanov.ccfit.nsu.core.network.nethandlers.impl.UnicastNetHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

private val Logger = KotlinLogging.logger(NetworkStateHolder::class.java.name)
private val kPortRange = 1..65535

class NetworkStateHolder(
  private val netController: NetworkController,
  private val gameController: GameController,
  override val unicastNetHandler: UnicastNetHandler,
  
) : NetworkStateContext, StateConsumer, GameSessionHandler {
  private val stateContextDispatcherScope = CoroutineScope(Dispatchers.Default)
  
  private val nodeChannels = NodeChannels(
    deadNodeChannel = ,
    updateNodesInfo = Channel(kChannelSize),
    detachNodeChannel = Channel(kChannelSize),
    reconfigureContextChannel = Channel(kChannelSize)
  )
  
  override val networkState: NodeState
    get() = networkStateHolder.get()
  
  private val networkStateHolder: AtomicReference<NodeState> = AtomicReference(
    LobbyState(this, gameController, nodeHandlers.netNodesHandler)
  )
  
  private val latestGameStateHolder =
    AtomicReference<SnakesProto.GameMessage.StateMsg?>()
  val latestGameState: SnakesProto.GameMessage.StateMsg?
    get() = latestGameStateHolder.get()
  private val masterDeputyHolder: AtomicReference<Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?> =
    AtomicReference()
  val masterDeputy: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>?
    get() = masterDeputyHolder.get()
  
  override fun handleJoinToGameReq(joinReq: Event.State.ByController.JoinReq) {
    when(val curState = networkState) {
      is LobbyState -> curState.requestJoinToGame(joinReq)
    }
  }
  
  override fun handleLaunchGame(
    launchGameReq: Event.State.ByController.LaunchGame
  ) {
    stateContextDispatcherScope.launch {
      reconfigureContext(launchGameReq)
    }
  }
  
  override fun handleConnectToGame(joinReqAck: Event.State.ByInternal.JoinReqAck) {
    stateContextDispatcherScope.launch {
      reconfigureContext(joinReqAck)
    }
  }
  
  override fun handleSwitchToLobby(
    switchToLobbyReq: Event.State.ByController.SwitchToLobby
  ) {
    stateContextDispatcherScope.launch {
      reconfigureContext(switchToLobbyReq)
    }
  }
  
  override fun handleSendStateToController(state: SnakesProto.GameState) {
    gameController.acceptNewState(state)
  }
  
  override fun submitState(
    state: StateMsg,
    acceptedPlayers: List<Pair<Pair<ClusterNode, String>, ActiveEntity?>>
  ) {
    val st = this.networkStateHolder.get();
    if(st !is MasterState) return
    val msdp = masterDeputyHolder.get() ?: return
  }
  
  override fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  ) = netController.sendUnicast(msg, nodeAddress)
  
  
  override suspend fun detachNode(
    node: ClusterNodeT<Node.MsgInfo>
  ) {
    /**
     * вообще здесь предполагалась логика по уведомлению того что viewer now,
     * но наверное это лучше сделать где то в другом месте, а ну да в
     * качестве функций сделать проверку которая уже будет уведомлять ноду
     */
    nodeChannels.detachNodeChannel.send(node)
  }
  
  override suspend fun terminateNode(
    node: ClusterNodeT<Node.MsgInfo>
  ) = nodeChannels.deadNodeChannel.send(node)
  
  override suspend fun joinNode(node: ClusterNode) {
    
  }
  
  suspend fun reconfigureContext(event: Event.State) {
    runBlocking { }
    nodeChannels.reconfigureContextChannel.send(event)
  }
  
  fun setupNewState(state: NodeState, changeAccessToken: Any) {
    checkChangeAccess(changeAccessToken)
    networkStateHolder.set(state)
  }
  
  private fun handleDetachNode(
    node: ClusterNodeT<Node.MsgInfo>, changeAccessToken: Any
  ) {
    when(val st = networkState) {
      is GameStateT -> st.atNodeDetachPostProcess(node, changeAccessToken)
    }
  }
  
  /**
   * Мы меняем состояние кластера в одной функции так что исполнение линейно
   */
  private fun CoroutineScope.clusterObserverActor(
    stateMachine: NetworkStateHolder
  ) = launch {
    try {
      Logger.info { "nodesNonLobbyWatcherRoutine launched" }
      while(isActive) {
        try {
          select {
            stateMachine.nodeChannels.detachNodeChannel.onReceive { node ->
              Logger.trace {
                "$node received in detachNodeChannel"
              }
              stateMachine.handleDetachNode(node, changeAccessToken)
            }
            
            
            stateMachine.nodeChannels.deadNodeChannel.onReceive { node ->
              Logger.trace {
                "$node received in deadNodeChannel"
              }
              stateMachine.handleDetachNode(node, changeAccessToken)
            }
            
            stateMachine.nodeChannels.updateNodesInfo.onReceive { node ->
              Logger.trace {
                "$node received in registerNewNodeChannel"
              }
              
            }
            
            stateMachine.nodeChannels.reconfigureContextChannel.onReceive { event ->
              Logger.trace {
                "$event received in registerNewNode"
              }
              when(event) {
                is Event.State.ByController.LaunchGame    -> {
                  launchGame(stateMachine, event)
                }
                
                is Event.State.ByInternal.JoinReqAck      -> {
                  Utils.onJoinGameAck(stateMachine, event)
                }
                
                is Event.State.ByController.SwitchToLobby -> {
                  switchToLobby(event, changeAccessToken)
                }
                
                else                                      -> {
                  Logger.error { "unsupported event by reconfigureContext $event" }
                }
              }
            }
          }
        } catch(e: IllegalChangeStateAttempt) {
          Logger.error(e) { "during cluster state change" }
        }
      }
    } catch(_: CancellationException) {
      cancel()
    } catch(e: Exception) {
      Logger.error(e) { "unexpected error" }
    } finally {
      Logger.info { "nodesWatcherRoutine finished" }
    }
  }
  
  private fun joinAsActive(
    event: Event.State.ByInternal.JoinReqAck
  ) {
    val curState = networkState
    if(curState !is LobbyState) throw IllegalChangeStateAttempt(
      fromState = curState.javaClass.name,
      toState = ActiveState::class.java.name,
    )
    curState.toActive(
      nodeHandlers.clusterNodesHandler, event, changeAccessToken
    )
    Logger.trace { "joined as ${NodeRole.NORMAL} to $event" }
  }
  
  private fun joinAsViewer(
    event: Event.State.ByInternal.JoinReqAck
  ) {
    val curState = networkState
    if(curState !is LobbyStateT) throw IllegalChangeStateAttempt(
      fromState = curState.javaClass.name,
      toState = ActiveState::class.java.name,
    )
    
    curState.toPassive(
      nodeHandlers.clusterNodesHandler, event, changeAccessToken
    )
    Logger.trace { "joined as ${NodeRole.VIEWER} to $event" }
  }
  
  private fun switchToPassive(
    localNode: LocalNode, gameConfig: InternalGameConfig
  ) {
    val curState = networkState
    if(curState !is ActiveState && curState !is MasterState) {
      throw IllegalChangeStateAttempt(
        fromState = curState.javaClass.name,
        toState = PassiveState::class.java.name,
      )
    }
  }
  
  private fun launchGame(
    stateMachine: NetworkStateHolder,
    event: Event.State.ByController.LaunchGame,
  ) {
    val curState = stateMachine.networkStateHolder.get()
    if(curState !is LobbyState) throw throw IllegalChangeStateAttempt(
      fromState = curState.javaClass.name,
      toState = MasterState::class.java.name
    )
    curState.toMaster(
      changeAccessToken, nodeHandlers.clusterNodesHandler, event
    )
  }
  
  /*fun switchToMaster(
    stateMachine: NetworkStateHolder,
    changeAccessToken: Any
  ) {
    checkChangeAccess(changeAccessToken)
    val curState = stateMachine.networkStateHolder.get()
    if(curState !is ActiveState) throw IllegalChangeStateAttempt(
      fromState = curState.javaClass.name,
      toState = MasterState::class.java.name
    )
    curState.toMaster(event, changeAccessToken)
  }*/
  
  fun reconfigureMasterDeputy(
    masterDeputyInfo: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>,
    token: Any
  ) {
    checkChangeAccess(token)
    masterDeputyHolder.set(masterDeputyInfo)
  }
  
  fun switchToLobby(
    event: Event.State.ByController.SwitchToLobby, changeAccessToken: Any
  ) {
    checkChangeAccess(changeAccessToken)
    val curState = networkState
    if(curState is LobbyState) throw IllegalChangeStateAttempt(
      fromState = curState.javaClass.name, toState = LobbyState::class.java.name
    )
    
    masterDeputyHolder.set(null)
    curState.cleanup()
    nodeHandlers.clusterNodesHandler.shutdown()
    
    LobbyState(
      stateHolder = this,
      netNodesHandler = nodeHandlers.netNodesHandler,
    ).apply { networkStateHolder.set(this) }
    Logger.trace { "state ${LobbyState::class.java} $event" }
    gameController.openLobby()
  }
  
  private fun setupNewState(
    newStateMsg: SnakesProto.GameMessage.StateMsg, changeAccessToken: Any
  ) {
    checkChangeAccess(changeAccessToken)
    
    val oldState = latestGameState?.state ?: return
    val newState = newStateMsg.state
    if(oldState.stateOrder >= newState.stateOrder) return
    latestGameStateHolder.set(newStateMsg)
    val curMsDp = masterDeputy ?: return
    checkMsInfoInState(curMsDp, newState)
    checkDpInfoInState(curMsDp, newState)
    handleSendStateToController(newState)
  }
  
  private data class NodeChannels(
  
  )
  
  private data class NodeHandlers(
    val clusterNodesHandler: ClusterNodesHandler,
    val netNodesHandler: NetNodeHandler
  )
  
  private fun checkChangeAccess(token: Any) {
    if(token !== changeAccessToken) throw IllegalChangeStateAttempt(
      "дружище предоставь токен того что находишься в корутине которая меняет" + " стейт"
    )
  }
}
