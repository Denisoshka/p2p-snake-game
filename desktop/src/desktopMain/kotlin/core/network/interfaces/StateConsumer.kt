package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.entity.observalbe.ObservableSnakeEntity
import java.net.InetSocketAddress

interface StateConsumer {
  fun submitState(
    state: SnakesProto.GameState.Builder,
  )
  
  fun submitAcceptedPlayer(
    playerData: Pair<Pair<InetSocketAddress, SnakesProto.GameMessage>,
      ObservableSnakeEntity?>
  )
}