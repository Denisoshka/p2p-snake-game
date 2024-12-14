package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe.ObservableSnakeEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.node.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import java.net.InetSocketAddress

class ActiveObserverContext(
  private val node: ClusterNode,
  private val snake: ObservableSnakeEntity,
  private var lastUpdateSeq: Long = 0,
) : NodePayloadT {
  
  @Synchronized
  override fun handleEvent(event: SteerMsg, seq: Long, node: ClusterNode?) {
    if(seq <= lastUpdateSeq) return
    lastUpdateSeq = seq
    snake.changeState(event)
  }
  
  override fun observerDetached(node: ClusterNode?) {
    this.snake.snakeState = SnakeState.ZOMBIE
  }
  
  override fun observableDetached(node: ClusterNode?) {
    this.node.detach()
  }
  
  override fun shootContextState(
    state: SnakesProto.GameState.Builder,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?,
    node: ClusterNode?
  ) {
    this.node.apply {
      val nodeRole = getNodeRole(this, masterAddrId, deputyAddrId) ?: return
      val pl = GamePlayer(
        name = this.name,
        id = this.nodeId,
        ipAddress = this.ipAddress.address.hostAddress,
        port = this.ipAddress.port,
        nodeRole = nodeRole,
        playerType = PlayerType.HUMAN,
        score = snake.score,
      )
      state.players.add(pl)
    }
  }
}