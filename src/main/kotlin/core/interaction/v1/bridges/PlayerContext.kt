package d.zhdanov.ccfit.nsu.core.interaction.v1.bridges

import d.zhdanov.ccfit.nsu.core.game.engine.core.entity.Snake
import d.zhdanov.ccfit.nsu.core.game.states.PlayerContextT
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import java.util.concurrent.atomic.AtomicLong

class PlayerContext(
  private val lastUpdateSeq: AtomicLong = AtomicLong(0L),
  override val playerId: Int,
  override val snake: Snake,
) : PlayerContextT {
  fun update(steer: SteerMsg, seq: Long) {
    synchronized(this) {
      val prev = lastUpdateSeq.get()
      if (seq <= prev) return

      lastUpdateSeq.set(seq)
      snake.changeState(steer.direction)
    }
  }
}