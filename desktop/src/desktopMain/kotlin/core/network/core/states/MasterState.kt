package d.zhdanov.ccfit.nsu.core.network.core.states

import core.network.core.Node
import core.network.core.NodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto.GameMessage
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.GameEngine
import d.zhdanov.ccfit.nsu.core.game.engine.entity.standart.SnakeEnt
import d.zhdanov.ccfit.nsu.core.interaction.v1.LocalPlayerContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.NetPlayerContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import d.zhdanov.ccfit.nsu.core.utils.MessageTranslator
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val logger = KotlinLogging.logger(MasterState::class.java.name)
private const val PlayerNameIsBlank = "player name blank"
private const val NodeAlreadyRunning = "node already running"

class MasterState(
  private val ncStateMachine: NetworkStateMachine,
  private val netController: NetworkController,
  private val nodesHandler: NodesHandler,
) : NetworkState {
  @Volatile var playerAndGame: Pair<LocalPlayerContext, GameEngine>? =
    null

  override fun joinHandle(
    ipAddress: InetSocketAddress, message: GameMessage, msgT: MessageType
  ) {
    val request = MessageTranslator.fromMessageT(
      message, msgT
    )
    try {
      val inMsg = request.msg as JoinMsg
      if(inMsg.playerName.isBlank()) {
        throw IllegalNodeRegisterAttempt(PlayerNameIsBlank)
      }
      if(inMsg.nodeRole != NodeRole.VIEWER || inMsg.nodeRole != NodeRole.NORMAL) {
        throw IllegalNodeRegisterAttempt("$ipAddress invalid node role: ${inMsg.nodeRole}")
      }
      nodesHandler.getNode(ipAddress)?.let {
        if(it.running) return
        throw IllegalNodeRegisterAttempt("$ipAddress invalid node role: ${inMsg.nodeRole}")
      }

    } catch(e: IllegalNodeRegisterAttempt) {
      logger.trace { e }

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
    if(!node.running) return
    val inp2p = MessageTranslator.fromMessageT(
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
    if(!node.running) return
    val inp2p = MessageTranslator.fromMessageT(
      message, MessageType.SteerMsg
    )
    node.payloadT?.handleEvent(inp2p.msg as SteerMsg, inp2p.msgSeq)
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
      newDep.getNextMSGSeqNum()
    )
    val outMsg = MessageTranslator.toMessageT(
      outP2PRoleChange, MessageType.RoleChangeMsg
    )
    netController.sendUnicast(outMsg, newDep.ipAddress)
  }

  override fun submitSteerMsg(steerMsg: SteerMsg) {
    playerAndGame?.first?.handleEvent(
      steerMsg, ncStateMachine.nextSegNum
    )
  }

  override fun initialize() {

  }

  fun init(gameConfig: InternalGameConfig, gameState: StateMsg?) {
    val stOrder = gameState?.stateOrder ?: 0
    val eng = GameEngine(gameConfig, ,stOrder)
    if(gameState != null) {
      val xyi: MutableMap<Int, SnakeEnt> = HashMap()
      for(sninfo in gameState.snakes) {
        val sn = SnakeEnt(sninfo.direction, sninfo.playerId)
        sn.restoreHitbox(sninfo.cords)
        sn.snakeState = sninfo.snakeState
        if(sn.snakeState == SnakeState.ALIVE) xyi[sninfo.playerId] = sn
      }
      for(plinfo in gameState.snakes) {
        val pl = NetPlayerContext
      }
    } else {
      val sn = eng.spawnSnake(
        ncStateMachine.nodeId
      ) ?: throw RuntimeException("ну пиздец")
      val pl = LocalPlayerContext(gameConfig.playerName, this, sn)
      playerAndGame = pl to eng
    }

  }

  override fun cleanup() {
    TODO("Not yet implemented")
  }
}