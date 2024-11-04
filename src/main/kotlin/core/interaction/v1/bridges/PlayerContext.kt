package d.zhdanov.ccfit.nsu.core.interaction.v1.bridges

import d.zhdanov.ccfit.nsu.core.game.entity.Snake
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import java.util.concurrent.atomic.AtomicLong

class PlayerContext(
  val playerId: Int,
  val snake: Snake,
  private val lastUpdateSeq: AtomicLong = AtomicLong(0L),
) {
  fun update(steer: SteerMsg, seq: Long) {
    synchronized(this) {
      val prev = lastUpdateSeq.get()
      if (seq <= prev) return

      lastUpdateSeq.set(seq)
      snake.changeState(steer.direction)
    }
  }
}