package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe.ObservableSnakeEntity
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.SnakeState
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.network.core.node.NodePayloadT
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import java.net.InetSocketAddress

class ActiveObserverContext(
  private val node: ClusterNodeT<Node.MsgInfo>,
  private val snake: ObservableSnakeEntity,
) : NodePayloadT {
  private var lastUpdateSeq: Long = 0
  
  init {
    snake.addObserver { this.observableDetached() }
  }
  
  @Synchronized
  override fun handleEvent(
    event: SnakesProto.GameMessage.SteerMsg, seq: Long,
  ): Boolean {
    if(seq <= lastUpdateSeq) return false
    lastUpdateSeq = seq
    snake.changeState(Direction.fromProto(event.direction))
    return true
  }
  
  override fun observerDetached() {
    snake.snakeState = SnakeState.ZOMBIE
  }
  
  override fun observableDetached() {
    node.detach()
  }
  
  override fun shootContextState(
    state: SnakesProto.GameState.Builder,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?,
  ) {
    this.node.apply {
      val nodeRole = getNodeRole(this, masterAddrId, deputyAddrId) ?: return
      val msbBldr = MessageUtils.MessageProducer.getGamePlayerMsg(
        name = name,
        id = nodeId,
        ipAddress = ipAddress.address.hostAddress,
        port = ipAddress.port,
        nodeRole = nodeRole,
        playerType = PlayerType.HUMAN,
        score = snake.score
      )
      state.apply {
        playersBuilder.addPlayers(msbBldr)
      }
    }
  }
}