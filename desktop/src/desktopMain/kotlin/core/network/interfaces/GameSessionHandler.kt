package d.zhdanov.ccfit.nsu.core.network.interfaces

import d.zhdanov.ccfit.nsu.core.network.core.states.events.StateEvent

interface GameSessionHandler {
  fun joinToGame(joinReq: StateEvent.ControllerEvent.JoinReq)
  fun launchGame(launchGameReq: StateEvent.ControllerEvent.LaunchGame)
  fun switchToLobby(switchToLobbyReq: StateEvent.ControllerEvent.SwitchToLobby)
}