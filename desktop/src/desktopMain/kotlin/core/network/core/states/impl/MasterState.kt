package d.zhdanov.ccfit.nsu.core.network.core.states.impl

import core.network.core.Node
import core.network.core.NodesHandler
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
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalMasterLaunchAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.net.InetSocketAddress

private val logger = KotlinLogging.logger(MasterState::class.java.name)
private const val PlayerNameIsBlank = "player name blank"
private const val NodeAlreadyRunning = "node already running"
private const val ConfigIsNull = "game config not set"
private const val StateIsNotSet = "state is not set"
private const val JoinInUpdateQ = 10

class MasterState(
  private val ncStateMachine: NetworkStateMachine,
  private val netController: NetworkController,
  private val nodesHandler: NodesHandler,
  gameConfig: GameConfig,
  playerInfo: GamePlayer,
  state: StateMsg? = null,
) : NetworkState {
  private val gameEngine: GameContext = GameEngine(
    JoinInUpdateQ, ncStateMachine, gameConfig
  )
  private val player: LocalObserverContext

  @Volatile private var nodesInitScope: CoroutineScope? = null

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

    initSubscriberNodes(state, entities)
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
      nodesHandler.getNode(ipAddress)?.let {
        val st = it.state
        if(NodeT.isRunning(st)) return
        throw IllegalNodeRegisterAttempt(
          "$ipAddress node already registered: ${inMsg.nodeRole}"
        )
      }

    } catch(e: IllegalNodeRegisterAttempt) {
      logger.trace { e }
      TODO("сделать посыл нахуй")
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
    val node = nodesHandler.getNode(ipAddress) ?: return
//    if(!node.running) return
    val inp2p = MessageTranslator.fromProto(
      message, MessageType.RoleChangeMsg
    )
    (inp2p.msg as RoleChangeMsg).let {
      if(it.senderRole != NodeRole.VIEWER && it.receiverRole != null) return
    }
    val outp2p = ncStateMachine.getP2PAck(message, node)
    val outmsg = MessageTranslator.toMessageT(
      outp2p, MessageType.AckMsg
    )
    netController.sendUnicast(outmsg, ipAddress)
    node.addMessageForAck(outmsg)
    node.handleEvent(NodeT.NodeEvent.ShutdownFromCluster)
  }

  override fun steerHandle(
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    val node = nodesHandler.getNode(ipAddress) ?: return
//    if(!node.running) return
    val inp2p = MessageTranslator.fromProto(
      message, MessageType.SteerMsg
    )
    node.payload?.handleEvent(inp2p.msg as SteerMsg, inp2p.msgSeq)
  }


  override fun handleNodeDetach(
    node: Node
  ) {
    val (msInfo, depInfo) = ncStateMachine.masterDeputy.get() ?: return

    if(depInfo == null || node.id != depInfo.second) return

    val newDep = ncStateMachine.chooseSetNewDeputy()
    val newDepInfo = newDep?.run { Pair(this.ipAddress, this.id) }
    ncStateMachine.masterDeputy.set(Pair(msInfo, newDepInfo))

    newDep ?: return

    val outP2PRoleChange = ncStateMachine.getP2PRoleChange(
      NodeRole.MASTER,
      NodeRole.DEPUTY,
      ncStateMachine.nodeId,
      newDep.id,
      ncStateMachine.nextSegNum
    )

    val outMsg = MessageTranslator.toMessageT(
      outP2PRoleChange, MessageType.RoleChangeMsg
    )

    netController.sendUnicast(outMsg, newDep.ipAddress)
  }

  override fun submitSteerMsg(steerMsg: SteerMsg) {
    player.handleEvent(
      steerMsg, ncStateMachine.nextSegNum
    )
  }

  override fun cleanup() {
    gameEngine.shutdown()
    nodesHandler.shutdown()
    nodesInitScope?.cancel()
  }

  private fun initSubscriberNodes(
    state: StateMsg?, entities: List<ActiveEntity>
  ) {
    if(state != null) {
      val entMap = entities.associateBy { it.id }
      nodesInitScope = CoroutineScope(Dispatchers.Default)

      val blowjobs = state.players.map {
        nodesInitScope?.async {
          kotlin.runCatching {
            val sn = entMap[it.id] ?: throw IllegalNodeRegisterAttempt(
              "snake ${it.id} node not found"
            )

            val node = Node(
              messageComparator =,
              id = it.id,
              ipAddress = InetSocketAddress(it.ipAddress!!, it.port!!),
              nodeRole = it.nodeRole,
              payload = null,
              nodesHandler = nodesHandler
            )

            node.payload = if(it.nodeRole == NodeRole.VIEWER) {
              ObserverContext(node, it.name)
            } else {
              ActiveObserverContext(node, it.name, sn as SnakeEntity)
            }

            nodesHandler.registerNode(node)
          }.recover { e ->
            logger.error(e) {
              "receive when init node id: ${it.id}, addr :${it.ipAddress}:${it.port}"
            }
            when(e) {
              is IllegalArgumentException, is NullPointerException, is IllegalNodeRegisterAttempt -> null

              else                                                                                -> throw e
            }
          } ?: return@async

          TODO("make node put")
        }
      }

    }
  }
}