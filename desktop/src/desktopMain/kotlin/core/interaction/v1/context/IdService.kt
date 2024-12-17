package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.SnakesProto
import java.util.concurrent.atomic.AtomicInteger

/**
 *  Вообще этот класс был создан чтобы когда мы мастер мы чекали на первый
 * допустимый ID
 * */
class IdService(state: SnakesProto.GameState? = null) {
  private val nextNodeIdProvider = AtomicInteger(0)
  val nextId
    get() = nextNodeIdProvider.incrementAndGet()
  
  init {
    state?.let {
      val mxPlId = it.players.playersList.maxByOrNull { player ->
        player.id
      }?.id ?: 0
      val mxSnId = it.snakesList.maxByOrNull { snake ->
        snake.playerId
      }?.playerId ?: 0
      val mxId = maxOf(mxPlId, mxSnId)
      nextNodeIdProvider.set(mxId)
    }
  }
}
