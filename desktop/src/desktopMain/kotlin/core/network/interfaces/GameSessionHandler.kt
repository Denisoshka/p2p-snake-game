package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.network.core.states.events.StateEvent
import kotlinx.coroutines.CoroutineScope

interface GameSessionHandler {
  fun CoroutineScope.joinToGame(joinReq: StateEvent.ControllerEvent.JoinReq)
  fun CoroutineScope.launchGame(launchGameReq: StateEvent.ControllerEvent.LaunchGame)
  fun CoroutineScope.switchToLobby(switchToLobbyReq: StateEvent.ControllerEvent.SwitchToLobby)
}