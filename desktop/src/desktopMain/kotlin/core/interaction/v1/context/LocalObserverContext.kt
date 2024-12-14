package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe.ObservableSnakeEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.network.core.node.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.LocalNode
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import java.net.InetSocketAddress

class LocalObserverContext(
  private val snake: ObservableSnakeEntity,
  private val localNode: LocalNode,
  private var lastUpdateSeq: Long = 0L,
) : NodePayloadT {
  @Synchronized
  override fun handleEvent(
    event: SnakesProto.GameMessage.SteerMsg,
    seq: Long,
    node: ClusterNode?
  ): Boolean {
    snake.changeState(
      MessageUtils.MessageProducer.DirectionFromProto(event.direction)
    )
    return true
  }
  
  override fun observerDetached(node: ClusterNode?) {
    snake.observerExpired()
  }
  
  override fun observableDetached(node: ClusterNode?) {
    localNode.detach()
  }
  
  override fun shootContextState(
    state: SnakesProto.GameState.Builder,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?,
    node: ClusterNode?
  ) {
    localNode.apply {
      val pl = GamePlayer(
        name = name,
        id = localNode.nodeId,
        ipAddress = null,
        port = null,
        nodeRole = NodeRole.MASTER,
        playerType = PlayerType.HUMAN,
        score = snake.score
      )
      state.players.add(pl)
    }
  }
}