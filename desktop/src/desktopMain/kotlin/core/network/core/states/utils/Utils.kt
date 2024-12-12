package core.network.core.states.utils

import core.network.core.connection.game.impl.ClusterNodesHandler
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.LocalObserverContext
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalChangeStateAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger { Utils::class.java }

object Utils {
  private suspend fun checkMsInfoInState(
    curMsDp: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>,
    state: SnakesProto.GameState
  ) {
    val stateMs = state.players.playersList.find {
      it.role == SnakesProto.NodeRole.MASTER
    }
    if(stateMs == null) {
      Logger.trace { "master absent in state $state" }
//    switchToLobby(Event.State.ByController.SwitchToLobby)
      return
    }
  }
  
  fun checkDpInfoInState(
    curMsDp: Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>,
    state: SnakesProto.GameState
  ): Pair<Pair<InetSocketAddress, Int>, Pair<InetSocketAddress, Int>?>? {
    val (curMs, curDp) = curMsDp
    val stateDp = state.players.playersList.find {
      it.role == SnakesProto.NodeRole.DEPUTY
    }
    if(stateDp == null) {
      Logger.trace { "deputy absent in state $state" }
      return (curMs to null)
    } else if(stateDp.id != curDp?.second) {
      try {
        Logger.trace {
          "setup new deputy (id:${stateDp.id}, addr:${stateDp.ipAddress}, port:${stateDp.id})"
        }
        val ipAddr = InetSocketAddress(stateDp.ipAddress, stateDp.port)
        return curMs to (ipAddr to stateDp.id)
      } catch(e: Exception) {
        Logger.error(e) { "during setup new deputy (id:${stateDp.id}, addr:${stateDp.ipAddress}, port:${stateDp.id})" }
      }
      return null
    }
    return curMsDp
  }
  
  fun submitState(
    player: LocalObserverContext,
    stateSeq: Int,
    clusterNodesHandler: ClusterNodesHandler,
    stateHolder: NetworkStateHolder,
    state: SnakesProto.GameState.Builder,
  ) {
    val msdp = stateHolder.masterDeputy ?: return
    
    val (ms, dp) = msdp
    player.shootContextState(state, ms, dp)
    
    for((_, node) in clusterNodesHandler) {
      node.payload?.shootContextState(state, ms, dp)
    }
    state.apply { stateOrder = stateSeq }.build()
    val stateMsg = SnakesProto.GameMessage.StateMsg.newBuilder().apply {
      setState(state)
    }.build()
    
    for((_, node) in clusterNodesHandler) {
      node.payload ?: continue
      
      val msg = MessageUtils.MessageProducer.getStateMsg(
        stateHolder.nextSeqNum, stateMsg
      )
      
      node.addMessageForAck(msg)
      node.sendToNode(msg)
    }
  }
  
  
  fun onPingMsg(
    clusterNodesHandler: ClusterNodesHandler,
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    nodeId: Int
  ) {
    clusterNodesHandler[ipAddress]?.let {
      val ack = MessageUtils.MessageProducer.getAckMsg(
        message.msgSeq, nodeId, it.nodeId
      )
      it.sendToNode(ack)
    }
  }
  
  fun nonLobbyOnAck(
    clusterNodesHandler: ClusterNodesHandler,
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
    msgT: MessageType
  ) {
    clusterNodesHandler[ipAddress]?.ackMessage(message)
  }
  
  fun onStateMsg(
    ipAddress: InetSocketAddress, message: SnakesProto.GameMessage
  ) {
    val (ms, _) = masterDeputyHolder.get() ?: return
    if(ms.first != ipAddress) return
    val stateSeq = message.state.state.stateOrder
    
  }
  
  suspend fun onJoinGameAck(
    stateMachine: NetworkStateHolder, event: Event.State.ByInternal.JoinReqAck
  ) {
    Logger.trace { "join to game with $event" }
    when(event.onEventAck.playerRole) {
      NodeRole.VIEWER -> {
        joinAsViewer(event)
      }
      
      NodeRole.NORMAL -> {
        joinAsActive(event)
      }
      
      else            -> {
        Logger.error { "incorrect $event" }
        throw IllegalChangeStateAttempt("incorrect $event")
      }
    }
  }
  
}
