package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe.ObservableSnakeEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.network.core.node.impl.LocalNode

class LocalObserverContext(
  localNode: LocalNode,
  snake: ObservableSnakeEntity,
) : ActiveObserverContext(localNode, snake) {
  @Synchronized
  override fun handleEvent(
    event: SnakesProto.GameMessage.SteerMsg, seq: Long,
  ): Boolean {
    snake.changeState(Direction.fromProto(event.direction))
    return true
  }
}