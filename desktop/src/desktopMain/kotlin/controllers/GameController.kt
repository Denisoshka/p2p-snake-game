package d.zhdanov.ccfit.nsu.controllers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.dto.AnnouncementInfo
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.AnnouncementMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateHolder
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import d.zhdanov.ccfit.nsu.view.screens.GameScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class GameController(
  private val ncStateHandler: NetworkStateHolder = TODO()
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
  private var gameState by mutableStateOf<SnakesProto.GameState?>(null)
  
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
  
  fun launchGame()
  
  fun openGameScreen(
    conf: InternalGameConfig
  ) {
    mainScope.launch {
      currentScreen = GameScreen(
        conf,
        null,
        { ncStateHandler.handleSwitchToLobby(Event.State.ByController.SwitchToLobby) },
      )
    }
  }
  
  fun openLobby() {
    mainScope.launch {
      currentScreen = Screen.Lobby
    }
  }
  
  fun acceptNewState(state: SnakesProto.GameState) {
    mainScope.launch {
      gameState = state
    }
  }
}
class GameController : ViewModel() {
  // Список анонсов игр
  private val _announcements = MutableStateFlow<List<AnnouncementInfo>>(emptyList())
  val announcements: StateFlow<List<AnnouncementInfo>> = _announcements
  
  // Состояние экрана (лобби или игра)
  private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Lobby)
  val screenState: StateFlow<ScreenState> = _screenState
  
  // Состояние показа диалога конфигурации
  private val _showConfigDialog = MutableStateFlow(false)
  val showConfigDialog: StateFlow<Boolean> = _showConfigDialog
  
  private var selectedAnnouncement: AnnouncementInfo? = null
  
  // Обновление списка анонсов (с удалением старых)
  fun updateAnnouncements(newAnnouncements: List<AnnouncementInfo>) {
    val currentTime = Instant.now()
    _announcements.value = (newAnnouncements + _announcements.value)
      .distinctBy { it.msg.gameName }
      .filter { Duration.between(it.timestamp, currentTime).toMinutes() < 5 }
  }
  
  // Открыть конфигурацию для присоединения
  fun openConfigDialogForJoinGame(announcement: AnnouncementInfo) {
    selectedAnnouncement = announcement
    _showConfigDialog.value = true
  }
  
  // Закрыть конфигурацию
  fun closeConfigDialog() {
    _showConfigDialog.value = false
    selectedAnnouncement = null
  }
  
  // Подтвердить конфигурацию
  fun submitConfig(config: InternalGameConfig) {
    _showConfigDialog.value = false
    selectedAnnouncement?.let {
      // Пробуем присоединиться к игре
      sendJoinRequest(it, config)
    } ?: run {
      // Запуск игры локально
      startLocalGame(config)
    }
  }
  
  private fun startLocalGame(config: InternalGameConfig) {
    // Запуск локальной игры
    _screenState.value = ScreenState.Game(config)
  }
  
  private fun sendJoinRequest(announcement: AnnouncementInfo, config: InternalGameConfig) {
    // Просто отправляем запрос на присоединение
    // Мы не блокируем UI, просто отправляем запрос
    
    // Пример отправки запроса
    GameService.sendJoinRequest(announcement, config)
    
    // Модель, получив ответ, обновит состояние экрана
    // Переход в игру произойдет, когда ответ будет получен
    // Обработчик ответа будет в модели, и он переключит экран на игровой
  }
  
  // Уведомление об ошибке при попытке присоединиться
  private fun showError(message: String) {
    // Показать ошибку, например, через Toast или какой-либо другой UI элемент
    println("Error: $message")
  }
}