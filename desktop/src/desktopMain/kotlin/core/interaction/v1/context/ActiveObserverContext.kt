package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import core.network.core.connection.game.impl.ClusterNode
import d.zhdanov.ccfit.nsu.core.game.engine.entity.Entity
import d.zhdanov.ccfit.nsu.core.game.engine.entity.active.SnakeEntity
import d.zhdanov.ccfit.nsu.core.game.engine.impl.GameEngine
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import java.net.InetSocketAddress

class ActiveObserverContext(
  clusterNode: ClusterNode,
  name: String,
  private val snake: SnakeEntity,
  private var lastUpdateSeq: Long = 0,
) : Entity by snake, ObserverContext(clusterNode, name) {
  override val score: Int
    get() = snake.score

  @Synchronized
  override fun handleEvent(event: SteerMsg, seq: Long) {
    if(seq <= lastUpdateSeq) return
    lastUpdateSeq = seq
    snake.changeState(event)
  }

  override fun onContextObserverTerminated() {
    snake.snakeState = SnakeState.ZOMBIE
  }

  override fun shootContextState(
    state: StateMsg,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?
  ) {
    val nodeRole = getNodeRole(masterAddrId, deputyAddrId) ?: return

    val pl = GamePlayer(
      name = name,
      id = node.nodeId,
      ipAddress = node.ipAddress.address.hostAddress,
      port = node.ipAddress.port,
      nodeRole = nodeRole,
      playerType = PlayerType.HUMAN,
      score = score,
    )
    state.players.add(pl)
  }

  override fun atDead(context: GameEngine) {
    snake.atDead(context)
    node.detach()
  }
}