package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.states.events.Event
import kotlinx.coroutines.CoroutineScope

interface GameSessionHandler {
  fun CoroutineScope.joinToGame(joinReq: Event.State.ByController.JoinReq)
  fun CoroutineScope.launchGame(launchGameReq: Event.State.ByController.LaunchGame)
  fun CoroutineScope.switchToLobby(switchToLobbyReq: Event.State.ByController.SwitchToLobby)
  fun sendStateToController(state: SnakesProto.GameState)
}