package d.zhdanov.ccfit.nsu.core.interaction.v1

import d.zhdanov.ccfit.nsu.core.game.engine.GameEngine
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Player
import d.zhdanov.ccfit.nsu.core.game.engine.entity.standart.Snake
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import java.util.concurrent.atomic.AtomicLong

class PlayerContext(
  snake: Snake,
  private val nodeT: NodeT,
  private val lastUpdateSeq: AtomicLong = AtomicLong(0L), name: String,
) : Player(name, snake), NodePayloadT {
  override fun handleEvent(event: SteerMsg, seq: Long) {
    synchronized(lastUpdateSeq) {
      if(seq <= lastUpdateSeq.get()) return

      lastUpdateSeq.set(seq)
      snake.changeState(event.direction)
    }
  }

  override fun onContextObserverTerminated() {
    snake.snakeState = SnakeState.ZOMBIE
  }

  fun shootState(context: GameEngine, state: StateMsg, role: NodeRole) {
    snake.shootState(context, state)
    val sockAddr = nodeT.ipAddress;
    val pl = GamePlayer(
      name,
      snake.id,
      sockAddr.address.hostAddress,
      sockAddr.port,
      role,
      PlayerType.HUMAN,
      snake.score
    )
    state.players.add(pl)
  }
}