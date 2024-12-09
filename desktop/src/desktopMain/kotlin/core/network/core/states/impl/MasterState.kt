package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.connection.game.impl.ClusterNode
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.GameContext
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.SnakeEntity
import d.zhdanov.ccfit.nsu.core.game.engine.impl.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.ActiveObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.GamePlayerInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.LocalObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.ObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalMasterLaunchAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.MasterStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(MasterState::class.java.name)
private const val PlayerNameIsBlank = "player name blank"
private const val JoinInUpdateQ = 10

class MasterState(
  override val gameConfig: InternalGameConfig,
  private val ncStateMachine: NetworkStateMachine,
  private val netController: NetworkController,
  private val clusterNodesHandler: ClusterNodesHandler,
  gamePlayerInfo: GamePlayerInfo,
  state: StateMsg? = null,
) : MasterStateT {
  private val nodesInitScope: CoroutineScope = CoroutineScope(
    Dispatchers.Default
  )
  private val gameEngine: GameContext = GameEngine(
    JoinInUpdateQ, ncStateMachine, gameConfig.gameSettings
  )
  val player: LocalObserverContext
  
  init {
    Logger.info { "$this init" }
    
    val entities = if(state != null) {
      gameEngine.initGameFromState(gameConfig.gameSettings, state, playerInfo)
    } else {
      gameEngine.initGame(gameConfig.gameSettings, playerInfo)
    }
    
    val localSnake = entities.find { it.id == playerInfo.id }
    if(localSnake != null) {
      player = LocalObserverContext(
        name = playerInfo.name,
        snake = localSnake as SnakeEntity,
        lastUpdateSeq = 0,
        ncStateMachine = ncStateMachine,
      )
    } else {
      throw IllegalMasterLaunchAttempt("local snake absent in state message")
    }
    
    state?.let {
      initSubscriberNodes(state, entities)
    }
    
    gameEngine.launch()
  }
  
  override fun joinHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    try {
      val joinMsg = message.join
      if(joinMsg.playerName.isBlank()) {
        throw IllegalNodeRegisterAttempt(PlayerNameIsBlank)
      }
      if(!(joinMsg.requestedRole == SnakesProto.NodeRole.VIEWER || joinMsg.requestedRole == SnakesProto.NodeRole.NORMAL)) {
        throw IllegalNodeRegisterAttempt(
          "$ipAddress invalid node role: ${joinMsg.requestedRole}"
        )
      }
      if(clusterNodesHandler[ipAddress] != null) {
        return
      }
      val initialNodeState = when(joinMsg.requestedRole) {
        SnakesProto.NodeRole.NORMAL -> Node.NodeState.Active
        else                        -> Node.NodeState.Passive
      }
      
      val node = ClusterNode(
        nodeState = initialNodeState,
        nodeId = ncStateMachine.nextNodeId,
        ipAddress = ipAddress,
        payload = null,
        clusterNodesHandler = clusterNodesHandler,
      )
      if(node.nodeState == Node.NodeState.Passive) {
        node.payload = ObserverContext(node, joinMsg.playerName)
      } else {
        TODO()
      }
      try {
        clusterNodesHandler.registerNode(node)
      } catch(e: IllegalNodeRegisterAttempt) {
        Logger.error(e) { "during registerNode" }
      }
    } catch(e: IllegalNodeRegisterAttempt) {
      val err = MessageUtils.MessageProducer.getErrorMsg(
        ncStateMachine.nextSeqNum, e.message ?: ""
      )
      ncStateMachine.sendUnicast(err, ipAddress)
      Logger.error(e) { "during joinHandle" }
    }
  }
  
  override fun pingHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) = ncStateMachine.onPingMsg(ipAddress, message, msgT)
  
  override fun ackHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) = ncStateMachine.nonLobbyOnAck(ipAddress, message, msgT)
  
  
  override fun roleChangeHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    if(!MessageUtils.RoleChangeIdentifier.fromNodeNodeLeave(message)) return
    val node = clusterNodesHandler[ipAddress] ?: return
    
    val ack = MessageUtils.MessageProducer.getAckMsg(
      message.msgSeq, ncStateMachine.nodeId, node.nodeId
    )
    netController.sendUnicast(ack, ipAddress)
    
    if(!node.running) node.detach()
  }
  
  override fun errorHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
  }
  
  override fun steerHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    val node = clusterNodesHandler[ipAddress] ?: return
    if(!node.running) return
    
    val inp2p = MessageTranslator.fromProto(
      message, MessageType.SteerMsg
    )
    node.payload?.handleEvent(inp2p.msg as SteerMsg, inp2p.msgSeq)
  }
  
  fun submitSteerMsg(steerMsg: SteerMsg) {
    player.handleEvent(
      steerMsg, ncStateMachine.nextSeqNum
    )
  }
  
  private fun initSubscriberNodes(
    state: StateMsg, entities: List<ActiveEntity>
  ) {
    Logger.info { "$this init subscriber nodes" }
    val entMap = entities.associateBy { it.id }
    nodesInitScope.also { scope ->
      state.players.map {
        scope.async {
          kotlin.runCatching {
            val sn = entMap[it.id] ?: throw IllegalNodeRegisterAttempt(
              "snake ${it.id} node not found"
            )
            
            val nodeState = when(it.nodeRole) {
              NodeRole.VIEWER                  -> Node.NodeState.Passive
              NodeRole.NORMAL, NodeRole.DEPUTY -> Node.NodeState.Active
              NodeRole.MASTER                  -> throw IllegalNodeRegisterAttempt(
                "illegal initial node state ${it.nodeRole}" + " during master state initialize"
              )
            }
            
            val clusterNode = ClusterNode(
              nodeId = it.id,
              ipAddress = InetSocketAddress(it.ipAddress!!, it.port!!),
              payload = null,
              clusterNodesHandler = clusterNodesHandler,
              nodeState = nodeState
            )
            
            clusterNode.payload = if(it.nodeRole == NodeRole.VIEWER) {
              ObserverContext(clusterNode, it.name)
            } else {
              ActiveObserverContext(clusterNode, it.name, sn as SnakeEntity)
            }
            
            clusterNodesHandler.registerNode(clusterNode)
          }.recover { e ->
            Logger.error(e) {
              "receive when init node id: ${it.id}, addr :${it.ipAddress}:${it.port}"
            }
            
            if(e !is IllegalArgumentException && e !is NullPointerException && e !is IllegalNodeRegisterAttempt) {
              throw e
            }
          }
        }
      }
    }
  }
  
  override fun cleanup() {
    Logger.info { "$this cleanup" }
    gameEngine.shutdown()
    clusterNodesHandler.shutdown()
    nodesInitScope.cancel()
  }
}
