package core.network.core.states.utils

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.context.LocalObserverContext
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNodesHolder
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.LocalNode
import d.zhdanov.ccfit.nsu.core.network.node.connected.ContextEvent
import d.zhdanov.ccfit.nsu.core.network.node.connected.StateHolder
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress

private val Logger = KotlinLogging.logger { Utils::class.java }
private val PortValuesRange = 1..65535

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
  
  }
  
  fun submitState(
    player: LocalObserverContext,
    stateSeq: Int,
    clusterNodesHolder: ClusterNodesHolder,
    stateHolder: StateHolder,
    state: SnakesProto.GameState.Builder,
  ) {
    val msdp = stateHolder.masterDeputy ?: return
    
    val (ms, dp) = msdp
    player.shootContextState(state, ms, dp)
    
    for((_, node) in clusterNodesHolder) {
      node.payload.shootContextState(state, ms, dp)
    }
    state.apply { stateOrder = stateSeq }.build()
    val stateMsg = SnakesProto.GameMessage.StateMsg.newBuilder().apply {
      setState(state)
    }.build()
    
    for((_, node) in clusterNodesHolder) {
      node.payload ?: continue
      
      val msg = MessageUtils.MessageProducer.getStateMsg(
        stateHolder.nextSeqNum, stateMsg
      )
      
      node.addMessageForAck(msg)
      node.sendToNode(msg)
    }
  }
  
  fun nonLobbyNonMasterOnPingMsg(
    stateHolder: StateHolder,
    nodesHolder: ClusterNodesHolder,
    localNode: LocalNode,
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
  ) {
    val (ms, _) = stateHolder.masterDeputy ?: return
    if(ms.first != ipAddress) return
    nodesHolder[ipAddress]?.let {
      val ack = MessageUtils.MessageProducer.getAckMsg(
        message.msgSeq, ms.second, localNode.nodeId
      )
      it.sendToNode(ack)
    }
  }
  
  fun nonLobbyOnAck(
    nodesHolder: ClusterNodesHolder,
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage,
  ) {
    nodesHolder[ipAddress]?.ackMessage(message)
  }
  
  /**
   * @return `masterInfo` `Pair<InetSocketAddress, Int>?` if message
   * submitted, else `null`
   * */
  fun onStateMsg(
    stateHolder: StateHolder,
    nodesHolder: ClusterNodesHolder,
    localNode: LocalNode,
    ipAddress: InetSocketAddress,
    message: SnakesProto.GameMessage
  ) {
    val (ms, _) = stateHolder.masterDeputy ?: return
    if(ms.first != ipAddress) return
    val curState = stateHolder.gameState ?: return
    val newStateSeq = message.state.state.stateOrder
    if(curState.stateOrder >= newStateSeq) return
    val ack = MessageUtils.MessageProducer.getAckMsg(
      msgSeq = message.msgSeq,
      senderId = ms.second,
      receiverId = localNode.nodeId
    )
    nodesHolder[ipAddress]?.sendToNode(ack)
    runBlocking {
      stateHolder.handleContextEvent(
        ContextEvent.Internal.NewState(message.state.state)
      )
    }
  }
  fun onNonMasterSubmitSteer(
    stateHolder: StateHolder,
    nodesHolder: ClusterNodesHolder,
    steerMsg: SnakesProto.GameMessage.SteerMsg
  ){
    val (ms, _) = stateHolder.masterDeputy ?: return
    nodesHolder[ms.first]?.let {
      val steer = MessageUtils.MessageProducer.getSteerMsg(
        stateHolder.nextSeqNum, steerMsg
      )
      it.sendToNode(steer)
      it.addMessageForAck(steer)
    }
  }
  fun onMasterSubmitSteer(  ){
  
  }
  
  fun atFromMasterNodeDeputyNow(
    master: Pair<InetSocketAddress, Int>,
    deputy: Pair<InetSocketAddress, Int>?,
    localNode: LocalNode,
    stateHolder: StateHolder,
    message: SnakesProto.GameMessage,
    ipAddress: InetSocketAddress,
  ): Boolean {
    if(master.second != message.senderId || ipAddress != master.first) return false
    if(message.receiverId != localNode.nodeId) return false
    if(deputy?.second == localNode.nodeId) return true
    runBlocking {
      stateHolder.handleContextEvent(ContextEvent.Internal.DeputyNow(localNode))
    }
    return true
  }
  
  fun atFromMasterPlayerDead(
    master: Pair<InetSocketAddress, Int>,
    localNode: LocalNode,
    message: SnakesProto.GameMessage,
    ipAddress: InetSocketAddress,
  ): Boolean {
    if(message.receiverId != localNode.nodeId) return false
    if(master.second != message.senderId || ipAddress != master.first) return false
    localNode.detach()
    return true
  }
  
  fun atFromMasterNodeMasterNow(
    master: Pair<InetSocketAddress, Int>,
    deputy: Pair<InetSocketAddress, Int>?,
    localNode: LocalNode,
    nodesHolder: ClusterNodesHolder,
    message: SnakesProto.GameMessage,
    ipAddress: InetSocketAddress,
  ): Boolean {
    if(master.second != message.senderId || ipAddress != master.first) return false
    if(deputy?.second != message.receiverId) return false
    if(deputy.second != localNode.nodeId) return false
    nodesHolder[ipAddress]?.detach()
    return true
  }
  
  fun atFromDeputyDeputyMasterNow(
    master: Pair<InetSocketAddress, Int>,
    deputy: Pair<InetSocketAddress, Int>?,
    nodesHolder: ClusterNodesHolder,
    message: SnakesProto.GameMessage,
    ipAddress: InetSocketAddress,
  ): Boolean {
    if(message.senderId != deputy?.second || ipAddress != deputy.first) return false
    nodesHolder[master.first]?.detach()
    return true
  }
  
  
  private fun correctMasterInfo(
    message: SnakesProto.GameMessage,
    ms: Pair<InetSocketAddress, Int>,
    ipAddress: InetSocketAddress
  ): Boolean {
    return ms.second == message.senderId && ipAddress == ms.first
  }
  
  fun findDeputyInState(
    msId: Int, state: SnakesProto.GameState
  ): SnakesProto.GamePlayer? {
    return state.players.playersList.firstOrNull {
      it.id != msId && it.ipAddress.isNotBlank() && it.port in PortValuesRange && it.role == SnakesProto.NodeRole.NORMAL
    }
  }
}
