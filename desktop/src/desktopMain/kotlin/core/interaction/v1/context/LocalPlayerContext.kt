package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.core.game.engine.GameEngine
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.standart.SnakeEnt
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import java.util.concurrent.atomic.AtomicLong

class LocalPlayerContext(
  private val name: String,
  private val snake: SnakeEnt,
  private val lastUpdateSeq: AtomicLong = AtomicLong(0L),
  private val ncStateMachine: NetworkStateMachine
) : NodePayloadT, Entity by snake {
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

  override fun shootNodeState(state: StateMsg) {
    val pl = GamePlayer(
      name,
      ncStateMachine.nodeId,
      null,
      null,
      NodeRole.MASTER,
      PlayerType.HUMAN,
      snake.score
    )
    state.players.add(pl)
  }

  override fun atDead(context: GameEngine) {
    snake.atDead(context)
    TODO()
  }
}