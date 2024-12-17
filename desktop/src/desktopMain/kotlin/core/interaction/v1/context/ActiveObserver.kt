package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.PlayerType
import d.zhdanov.ccfit.nsu.core.network.core.node.Node
import d.zhdanov.ccfit.nsu.core.utils.MessageUtils
import java.net.InetSocketAddress

class ActiveObserver(
  override val state: Node.NodeState,
  override var score: Int
) : Context.Active {
  override fun handleEvent(
    event: SnakesProto.GameMessage.SteerMsg,
    seq: Long
  ): Boolean {
    TODO("Not yet implemented")
  }
  
  override fun getEvent(): SnakesProto.GameMessage.SteerMsg? {
    TODO("Not yet implemented")
  }
  
  override fun shootContextState(
    state: SnakesProto.GameState.Builder,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?
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