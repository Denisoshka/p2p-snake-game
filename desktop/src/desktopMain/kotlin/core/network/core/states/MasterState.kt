package d.zhdanov.ccfit.nsu.core.network.core.states

import core.network.core.Node
import core.network.core.NodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.impl.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.LocalObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalMasterLaunchAttempt
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import io.github.oshai.kotlinlogging.KotlinLogging
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
) : NetworkState {
  private val gameEngine: GameEngine = GameEngine(JoinInUpdateQ, ncStateMachine)

  @Volatile var config: InternalGameConfig? = null
  @Volatile var player: LocalObserverContext? = null

  override fun joinHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
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
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) = ncStateMachine.onPingMsg(ipAddress, message, msgT)

  override fun ackHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
    ncStateMachine.onAckMsg(ipAddress, message)
  }


  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
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
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
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
    player?.handleEvent(
      steerMsg, ncStateMachine.nextSegNum
    )
  }

  /**
   * @throws IllegalMasterLaunchAttempt
   * */
  override fun initialize() {
    val conf = config ?: throw IllegalMasterLaunchAttempt(ConfigIsNull)
    val state = ncStateMachine.latestGameState.get()
    if(state == null) {
      gameEngine.initNewGame(conf)
    } else {
      gameEngine.initGameFromState(conf, state.first)
    }
  }

  override fun cleanup() {
  }
}