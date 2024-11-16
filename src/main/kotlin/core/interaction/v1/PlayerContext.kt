package d.zhdanov.ccfit.nsu.core.interaction.v1

import d.zhdanov.ccfit.nsu.core.game.engine.entity.PlayerT
import d.zhdanov.ccfit.nsu.core.game.engine.entity.stardart.Snake
import d.zhdanov.ccfit.nsu.core.game.engine.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
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
) : PlayerT(name, snake), NodePayloadT {
  override fun update(steer: SteerMsg, seq: Long) {
    synchronized(lastUpdateSeq) {
      if(seq <= lastUpdateSeq.get()) return

      lastUpdateSeq.set(seq)
      snake.changeState(steer.direction)
    }
  }

  override fun handleEvent(event: SteerMsg, seq: Long) = update(event, seq)

  override fun onObservedExpired() {
    TODO("implement me please")
  }

  override fun onObserverTerminated() {
    snake.snakeState = SnakeState.ZOMBIE
  }

  override fun shootState(context: GameEngine, state: StateMsg) {
    snake.shootState(context, state)
    val sockAddr = nodeT.address;
    val pl = GamePlayer(
      name, snake.id, sockAddr.address.hostAddress, sockAddr.port,
      nodeT.nodeState, PlayerType.HUMAN, snake.score
    )
    state.players.add(pl)
  }
}