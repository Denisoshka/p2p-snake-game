package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe.ObservableSnakeEntity
import d.zhdanov.ccfit.nsu.core.network.core.node.ClusterNodeT
import d.zhdanov.ccfit.nsu.core.network.core.node.Node

interface StateConsumer {
  fun submitState(
    state: SnakesProto.GameState.Builder,
  )
  
  fun submitAcceptedPlayer(
    playerData: Pair<Pair<ClusterNodeT<Node.MsgInfo>, SnakesProto.GameMessage>, ObservableSnakeEntity?>
  )
}