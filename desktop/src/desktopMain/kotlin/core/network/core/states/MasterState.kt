package d.zhdanov.ccfit.nsu.core.network.core.states

import core.network.core.Node
import core.network.core.NodesHandler
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.game.engine.GameEngine
import d.zhdanov.ccfit.nsu.core.game.engine.entity.standart.SnakeEnt
import d.zhdanov.ccfit.nsu.core.interaction.v1.LocalPlayerContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.NetPlayerContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.NodePayloadT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.JoinMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.RoleChangeMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkController
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import d.zhdanov.ccfit.nsu.core.network.interfaces.NetworkState
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val logger = KotlinLogging.logger(MasterState::class.java.name)

class MasterState<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>, Payload : NodePayloadT>(
  private val ncStateMachine: NetworkStateMachine<MessageT, InboundMessageTranslator, Payload>,
  private val netController: NetworkController<MessageT, InboundMessageTranslator, Payload>,
  private val nodesHandler: NodesHandler<MessageT, InboundMessageTranslator, Payload>,
) : NetworkState<MessageT, InboundMessageTranslator, Payload> {
  @Volatile var playerAndGame: Pair<LocalPlayerContext<MessageT, InboundMessageTranslator>, GameEngine>? =
    null
  private val msgTranslator = netController.messageTranslator
  override fun joinHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    val request = msgTranslator.fromMessageT(
      message, msgT
    )
    val inMsg = request.msg as JoinMsg
    if(inMsg.playerName.isBlank()) {
      logger.atTrace { "$ipAddress player name empty" }
      return
    }
    if(inMsg.nodeRole != NodeRole.VIEWER || inMsg.nodeRole != NodeRole.NORMAL) {
      logger.atTrace { "$ipAddress invalid node role: ${inMsg.nodeRole}{}" }
      return
    }
    val node = nodesHandler.getNode(ipAddress)?.let {
      return
    }
  }

  override fun pingHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = ncStateMachine.onPingMsg(ipAddress, message, msgT)

  override fun ackHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) = ncStateMachine.onAckMsg(ipAddress, message)


  override fun roleChangeHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    val node = nodesHandler.getNode(ipAddress) ?: return
    if(!node.running) return
    val inp2p = msgTranslator.fromMessageT(
      message, MessageType.RoleChangeMsg
    )
    (inp2p.msg as RoleChangeMsg).let {
      if(it.senderRole != NodeRole.VIEWER && it.receiverRole != null) return
    }
    val outp2p = ncStateMachine.getP2PAck(message, node)
    val outmsg = msgTranslator.toMessageT(
      outp2p, MessageType.AckMsg
    )
    netController.sendUnicast(outmsg, ipAddress)
    node.addMessageForAck(outmsg)
    node.handleEvent(NodeT.NodeEvent.ShutdownFromCluster)
  }

  override fun steerHandle(
    ipAddress: InetSocketAddress, message: MessageT, msgT: MessageType
  ) {
    val node = nodesHandler.getNode(ipAddress) ?: return
    if(!node.running) return
    val inp2p = netController.messageTranslator.fromMessageT(
      message, MessageType.SteerMsg
    )
    node.payloadT?.handleEvent(inp2p.msg as SteerMsg, inp2p.msgSeq)
  }


  override fun handleNodeDetach(
    node: Node<MessageT, InboundMessageTranslator, Payload>
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
    val outMsg = ncStateMachine.msgTranslator.toMessageT(
      outP2PRoleChange, MessageType.RoleChangeMsg
    )
    netController.sendUnicast(outMsg, newDep.ipAddress)
  }

  override fun submitSteerMsg(steerMsg: SteerMsg) {
    playerAndGame?.first?.handleEvent(
      steerMsg, ncStateMachine.seqNumProvider.getAndIncrement()
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
      for (plinfo in gameState.snakes){
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