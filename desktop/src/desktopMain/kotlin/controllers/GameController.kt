package d.zhdanov.ccfit.nsu.controllers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import d.zhdanov.ccfit.nsu.controllers.dto.AnnouncementInfo
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.AnnouncementMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.states.events.StateEvents
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class GameController(
  private val ncStateHandler: NetworkStateMachine = TODO()
) {
  private val upSteer = SteerMsg(Direction.UP)
  private val rightSteer = SteerMsg(Direction.RIGHT)
  private val leftSteer = SteerMsg(Direction.LEFT)
  private val downSteer = SteerMsg(Direction.DOWN)

  val cleanupInterval: Long = 1000
  val thresholdDelay: Long = 1000

  var announcementMsgsState = mutableStateListOf<AnnouncementInfo>()
    private set
  var currentScreen by mutableStateOf<Screen>(Screen.Lobby)

  private val mainScope = MainScope()
  private var cleanupJob: Job? = null
  private var showCreateGameDialog by mutableStateOf(false)

  fun addAnnouncementMsg(msg: AnnouncementMsg) {
    if(currentScreen != Screen.Lobby) return

    mainScope.launch {
      val existingIndex =
        announcementMsgsState.indexOfFirst { it.msg.gameName == msg.gameName }
      if(existingIndex != -1) {
        announcementMsgsState[existingIndex].timestamp = Instant.now()
      } else {
        announcementMsgsState.add(AnnouncementInfo(msg))
      }
    }
  }

  fun startMessageCleanup(
    threshold: Long, recheckIntervalSeconds: Long
  ) {
    cleanupJob?.cancel()
    cleanupJob = mainScope.launch {
      while(true) {
        val now = Instant.now()
        announcementMsgsState.removeAll {
          Duration.between(it.timestamp, now).seconds > threshold
        }
        delay(recheckIntervalSeconds)
      }
    }
  }

  fun stopMessageCleanup() {
    cleanupJob?.cancel()
    cleanupJob = null
  }

  fun openGame(gameConfig: GameConfig, announcement: AnnouncementMsg?) {
    currentScreen = Screen.Game(gameConfig, announcement)
    ncStateHandler.changeState(StateEvents.ControllerEvent.LaunchGame())
  }

  fun openLobby() {
    currentScreen = Screen.Lobby
    ncStateHandler.changeState(StateEvents.ControllerEvent.SwitchToLobby)
  }
}
