package d.zhdanov.ccfit.nsu.controllers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import d.zhdanov.ccfit.nsu.controllers.dto.AnnouncementInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.AnnouncementMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant

class GameController(
  private val ncStateHandler: NetworkStateMachine = TODO()
) {
  private val upSteer = SteerMsg(Direction.UP)
  private val rightSteer = SteerMsg(Direction.RIGHT)
  private val leftSteer = SteerMsg(Direction.LEFT)
  private val downSteer = SteerMsg(Direction.DOWN)
  private val announcementMsgsState = mutableStateListOf<AnnouncementInfo>()

  private var currentScreen by mutableStateOf(ControllerState.Lobby)
  private var selectedGame by mutableStateOf<AnnouncementMsg?>(null)
  private var showCreateGameDialog by mutableStateOf(false)

  fun addAnnouncementMsg(msg: AnnouncementMsg, from: InetSocketAddress) {
    if(!announcementMsgsState.any { it.msg.gameName == msg.gameName }) {

      announcementMsgsState = announcementMsgsState + AnnouncementInfo(msg)
    }
  }

  fun removeOldMessages(olderThanSeconds: Long) {
    val now = Instant.now()
    announcementMsgsState = announcementMsgsState.filter {
      Duration.between(it.timestamp, now).seconds <= olderThanSeconds
    }
  }

  fun openCreateGameDialog() {
    showCreateGameDialog = true
  }

  fun closeCreateGameDialog() {
    showCreateGameDialog = false
  }

  fun selectGame(game: AnnouncementMsg) {
    selectedGame = game
    currentScreen = ControllerState.Game
  }

  // Вернуться к списку объявлений
  fun backToAnnouncements() {
    currentScreen = ControllerState.Lobby
  }
}
