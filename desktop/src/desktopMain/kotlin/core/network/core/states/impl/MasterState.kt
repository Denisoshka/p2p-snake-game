package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.connection.game.impl.ClusterNode
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.GameContext
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.ActiveEntity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.SnakeEntity
import d.zhdanov.ccfit.nsu.core.game.engine.impl.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.ActiveObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.LocalObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.ObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalMasterLaunchAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.MasterStateT
import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeT
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger(MasterState::class.java.name)
private const val PlayerNameIsBlank = "player name blank"
private const val JoinInUpdateQ = 10

class MasterState(
  private val ncStateMachine: NetworkStateMachine,
  private val netController: NetworkController,
  private val clusterNodesHandler: ClusterNodesHandler,
  gameConfig: GameConfig,
  playerInfo: GamePlayer,
  state: StateMsg? = null,
) : MasterStateT {
  @Volatile private var nodesInitScope: CoroutineScope? = null;
  private val gameEngine: GameContext = GameEngine(
    JoinInUpdateQ, ncStateMachine, gameConfig
  )
  val player: LocalObserverContext

  init {
    val entities = if(state != null) {
      gameEngine.initGameFromState(gameConfig, state, playerInfo)
    } else {
      gameEngine.initGame(gameConfig, playerInfo)
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

    state?.let { initContextFromState(state, entities) }

    gameEngine.launch()
  }

  override fun joinHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    val request = MessageTranslator.fromProto(
      message, msgT
    )
    try {
      val inMsg = request.msg as JoinMsg
      if(inMsg.playerName.isBlank()) {
        throw IllegalNodeRegisterAttempt(PlayerNameIsBlank)
      }
      if(inMsg.nodeRole != NodeRole.VIEWER || inMsg.nodeRole != NodeRole.NORMAL) {
        throw IllegalNodeRegisterAttempt(
          "$ipAddress invalid node role: ${inMsg.nodeRole}"
        )
      }
      clusterNodesHandler[ipAddress]?.let {
        TODO()
      }
    } catch(e: IllegalNodeRegisterAttempt) {
      Logger.trace(e) { }
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
  ) {
    ncStateMachine.onAckMsg(ipAddress, message)
  }


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

  override fun submitSteerMsg(steerMsg: SteerMsg) {
    player.handleEvent(
      steerMsg, ncStateMachine.nextSegNum
    )
  }

  @Synchronized
  private fun initSubscriberNodes(
    state: StateMsg, entities: List<ActiveEntity>
  ) {
    val entMap = entities.associateBy { it.id }
    nodesInitScope ?: throw IllegalChangeStateAttempt("nodesInitScope non null")
    nodesInitScope = CoroutineScope(Dispatchers.Default).also { scope ->
      state.players.map {
        scope.async {
          kotlin.runCatching {
            val sn = entMap[it.id] ?: throw IllegalNodeRegisterAttempt(
              "snake ${it.id} node not found"
            )

            val nodeState = when(it.nodeRole) {
              NodeRole.VIEWER                  -> NodeT.NodeState.Passive
              NodeRole.NORMAL, NodeRole.DEPUTY -> NodeT.NodeState.Active
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

  private fun initContextFromState(
    state: StateMsg, entities: List<ActiveEntity>
  ) {
    initSubscriberNodes(state, entities)
  }

  @Synchronized
  override fun cleanup() {
    gameEngine.shutdown()
    clusterNodesHandler.shutdown()
    nodesInitScope?.cancel()
  }
}
