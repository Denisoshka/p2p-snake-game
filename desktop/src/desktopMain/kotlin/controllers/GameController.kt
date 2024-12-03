package d.zhdanov.ccfit.nsu.controllers

import d.zhdanov.ccfit.nsu.controllers.dto.GameAnnouncement
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.AnnouncementMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.ViewModel
import java.net.InetSocketAddress

class GameController(
  private val ncStateHandler: NetworkStateMachine = TODO()
) : ViewModel() {
  private val upSteer = SteerMsg(Direction.UP)
  private val rightSteer = SteerMsg(Direction.RIGHT)
  private val leftSteer = SteerMsg(Direction.LEFT)
  private val downSteer = SteerMsg(Direction.DOWN)

  private val availableGames = StateFlow<List<GameAnnouncement>>()

  fun submitSteerMsg() {
    ncStateHandler.submitSteerMsg()
  }

  fun onNewGameAnnouncement(
    gameAnnouncement: AnnouncementMsg,
    from: InetSocketAddress
  ) {

  }
}