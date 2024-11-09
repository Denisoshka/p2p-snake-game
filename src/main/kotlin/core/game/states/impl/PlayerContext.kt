package d.zhdanov.ccfit.nsu.core.game.states.impl

import d.zhdanov.ccfit.nsu.core.game.engine.core.entity.Snake
import d.zhdanov.ccfit.nsu.core.game.states.PlayerContextT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.interfaces.Node
import java.util.concurrent.atomic.AtomicLong

class PlayerContext<ContextId>(
  override val snake: Snake,
  private val node: Node<ContextId>,
  private val lastUpdateSeq: AtomicLong = AtomicLong(0L),
) : PlayerContextT {
  override fun update(steer: SteerMsg, seq: Long) {
    synchronized(this) {
      val prev = lastUpdateSeq.get()
      if (seq <= prev) return

      lastUpdateSeq.set(seq)
      snake.changeState(steer.direction)
    }
  }

}