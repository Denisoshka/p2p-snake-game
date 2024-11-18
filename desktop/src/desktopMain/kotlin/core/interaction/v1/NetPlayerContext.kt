package d.zhdanov.ccfit.nsu.core.interaction.v1

import core.network.core.Node
import d.zhdanov.ccfit.nsu.core.game.engine.GameEngine
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Player
import d.zhdanov.ccfit.nsu.core.game.engine.entity.standart.SnakeEnt
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.interfaces.NodeT
import java.util.concurrent.atomic.AtomicLong

class NetPlayerContext(
  name: String,
  snakeEnt: SnakeEnt,
  private val node: Node,
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
    val sockAddr = this.node.ipAddress;
    val pl = GamePlayer(
      name,
      snakeEnt.id,
      sockAddr.address.hostAddress,
      sockAddr.port,
      this.node.nodeRole,
      PlayerType.HUMAN,
      snakeEnt.score
    )
    state.players.add(pl)
  }

  override fun atDead(context: GameEngine) {
    super.atDead(context)
    node.handleEvent(NodeT.NodeEvent.ShutdownFromCluster)
  }
}