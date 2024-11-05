package d.zhdanov.ccfit.nsu.core.game

import d.zhdanov.ccfit.nsu.core.game.states.GameStateBridgeT
import d.zhdanov.ccfit.nsu.core.game.states.LobbyStateBridgeT
import d.zhdanov.ccfit.nsu.core.game.states.State
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg

class GameController(
  baseState: State
) : GameStateBridgeT, LobbyStateBridgeT {
  private var state = baseState
  fun updateState(newState: State) {
    state.terminate()
    state = newState
    newState.launch()
  }

  override fun launchNewGame() {
    TODO("Not yet implemented")
  }

  override fun exitGame() {
    TODO("Not yet implemented")
  }

  override fun submitGameState(state: StateMsg) {
    TODO("Not yet implemented")
  }

  override fun launchNewGame(config: GameConfig) {
    TODO("Not yet implemented")
  }

  override fun terminateApplication() {
    TODO("Not yet implemented")
  }
}