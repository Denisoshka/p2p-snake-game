package d.zhdanov.ccfit.nsu.core.interaction.v1

import d.zhdanov.ccfit.nsu.core.game.engine.core.entity.Snake
import d.zhdanov.ccfit.nsu.core.game.PlayerT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.interfaces.Node
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodePayloadT
import java.util.concurrent.atomic.AtomicLong

class PlayerContext<ContextId>(
   override val snake: Snake,
   private val node: Node<ContextId>,
   private val lastUpdateSeq: AtomicLong = AtomicLong(0L),
) : PlayerT(snake), NodePayloadT {
  override fun update(steer: SteerMsg, seq: Long) {
    synchronized(this) {
      val prev = lastUpdateSeq.get()
      if(seq <= prev) return

      lastUpdateSeq.set(seq)
      snake.changeState(steer.direction)
    }
  }

  override fun onObservedExpired() {
    TODO("implement me please")
  }

  override fun onObserverTerminated() {
    snake.snakeState = SnakeState.ZOMBIE
  }
}