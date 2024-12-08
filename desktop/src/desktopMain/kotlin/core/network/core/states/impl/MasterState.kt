package d.zhdanov.ccfit.nsu.core.network.core.states.impl

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
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalMasterLaunchAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl.GameNode
import d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl.GameNodesHandler
import d.zhdanov.ccfit.nsu.core.network.interfaces.core.NetworkState
import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeT
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.net.InetSocketAddress

private val logger = KotlinLogging.logger(MasterState::class.java.name)
private const val PlayerNameIsBlank = "player name blank"
private const val JoinInUpdateQ = 10

class MasterState(
  private val ncStateMachine: NetworkStateMachine,
  private val netController: NetworkController,
  private val gameNodesHandler: GameNodesHandler,
  gameConfig: GameConfig,
  playerInfo: GamePlayer,
  state: StateMsg? = null,
) : NetworkState {
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
      gameNodesHandler[ipAddress]?.let {
        TODO()
      }
    } catch(e: IllegalNodeRegisterAttempt) {
      logger.trace(e) { }
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
    val node = gameNodesHandler[ipAddress]?.let {
      if(!it.running) return
      it
    } ?: return

    val inp2p = MessageTranslator.fromProto(
      message, MessageType.RoleChangeMsg
    )

    (inp2p.msg as RoleChangeMsg).let {
      if(it.senderRole != NodeRole.VIEWER && it.receiverRole != null) return
    }

    val outp2p = ncStateMachine.getP2PAck(message, node)
    val outmsg = MessageTranslator.toMessageT(outp2p, MessageType.AckMsg)

    netController.sendUnicast(outmsg, ipAddress)
    node.addMessageForAck(outmsg)
    node.shutdown()
    node.handleEvent(NodeT.NodeEvent.ShutdownFromCluster)
  }

  override fun steerHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    val node = gameNodesHandler[ipAddress] ?: return
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

            val gameNode = GameNode(
              messageComparator =,
              nodeId = it.id,
              ipAddress = InetSocketAddress(it.ipAddress!!, it.port!!),
              payload = null,
              gameNodesHandler = gameNodesHandler,
              nodeState = nodeState
            )

            gameNode.payload = if(it.nodeRole == NodeRole.VIEWER) {
              ObserverContext(gameNode, it.name)
            } else {
              ActiveObserverContext(gameNode, it.name, sn as SnakeEntity)
            }

            gameNodesHandler.registerNode(gameNode)
          }.recover { e ->
            logger.error(e) {
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
    gameNodesHandler.shutdown()
    nodesInitScope?.cancel()
  }
}
