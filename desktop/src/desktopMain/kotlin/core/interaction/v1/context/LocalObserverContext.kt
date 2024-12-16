package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe.ObservableSnakeEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.network.core.node.NodePayloadT
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.LocalNode
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import java.net.InetSocketAddress

class LocalObserverContext(
  private val snake: ObservableSnakeEntity,
  private val localNode: LocalNode,
) : NodePayloadT {
  @Synchronized
  override fun handleEvent(
    event: SnakesProto.GameMessage.SteerMsg, seq: Long,
  ): Boolean {
    snake.changeState(Direction.fromProto(event.direction))
    return true
  }
  
  override fun observerDetached() {
    snake.observerExpired()
  }
  
  override fun observableDetached() {
    localNode.detach()
  }
  
  override fun shootContextState(
    state: SnakesProto.GameState.Builder,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?,
  ) {
    localNode.apply {
      val nodeRole = getNodeRole(this, masterAddrId, deputyAddrId) ?: return
      val msbBldr = MessageUtils.MessageProducer.getGamePlayerMsg(
        name = name,
        id = nodeId,
        ipAddress = null,
        port = null,
        nodeRole = nodeRole,
        playerType = PlayerType.HUMAN,
        score = snake.score
      )
      state.playersBuilder.addPlayers(msbBldr)
    }
  }
}