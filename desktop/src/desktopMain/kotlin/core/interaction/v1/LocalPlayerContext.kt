package d.zhdanov.ccfit.nsu.core.interaction.v1

import d.zhdanov.ccfit.nsu.core.game.engine.GameEngine
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Player
import d.zhdanov.ccfit.nsu.core.game.engine.entity.standart.SnakeEnt
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.states.MasterState
import d.zhdanov.ccfit.nsu.core.network.interfaces.MessageTranslatorT
import java.util.concurrent.atomic.AtomicLong

class LocalPlayerContext<MessageT, InboundMessageTranslator : MessageTranslatorT<MessageT>>(
  name: String,
  context: MasterState<MessageT, InboundMessageTranslator, LocalPlayerContext<MessageT, InboundMessageTranslator>>,
  snakeEnt: SnakeEnt,
  private val lastUpdateSeq: AtomicLong = AtomicLong(0L),
) : Player(name, snakeEnt), NodePayloadT {
  override fun handleEvent(event: SteerMsg, seq: Long) {
    synchronized(lastUpdateSeq) {
      if(seq <= lastUpdateSeq.get()) return

      lastUpdateSeq.set(seq)
      snakeEnt.changeState(event.direction)
    }
  }

  override fun onContextObserverTerminated() {
    snakeEnt.snakeState = SnakeState.ZOMBIE
  }

  override fun shootState(context: GameEngine, state: StateMsg) {
    snakeEnt.shootState(context, state)
    val pl = GamePlayer(
      name,
      snakeEnt.id,
      null,
      null,
      NodeRole.MASTER,
      PlayerType.HUMAN,
      snakeEnt.score
    )
    state.players.add(pl)
  }

  override fun atDead(context: GameEngine) {
    super.atDead(context)
    TODO()
  }
}