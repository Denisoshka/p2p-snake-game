package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.SnakesProto
import java.net.InetSocketAddress

interface StateService {
  fun submitState(
    state: SnakesProto.GameState.Builder,
  )
  
  fun submitNewRegisteredPlayer(
    ipAddr: InetSocketAddress, initMessage: SnakesProto.GameMessage, id: Int?
  )
}