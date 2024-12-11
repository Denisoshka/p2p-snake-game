package d.zhdanov.ccfit.nsu.view.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.controllers.Screen
import d.zhdanov.ccfit.nsu.core.game.InternalGameConfig
import d.zhdanov.ccfit.nsu.view.elements.game.GameField
import d.zhdanov.ccfit.nsu.view.elements.game.GameHeader
import d.zhdanov.ccfit.nsu.view.elements.game.PlayerList

class GameViewModel(
  config: InternalGameConfig
) : ViewModel() {
  val gameConfig = config
  var gameState = mutableStateOf<SnakesProto.GameState?>(null)
  
  
  fun updateGameState(state: SnakesProto.GameState) {
    gameState.value = state
  }
}


@Composable
fun GameScreen(
  gameConfig: InternalGameConfig,
  state: SnakesProto.GameState?,
  onExitGame: () -> Unit
): Screen {
  val gameName = gameConfig.gameName
  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp)
  ) {
    GameHeader(gameName = gameName, onExit = onExitGame)
    
    Spacer(modifier = Modifier.height(8.dp))
    if(state != null) {
      GameField(
        snakes = gameState.snakesList,
        food = gameState.foods,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        currentPlayerId = currentPlayerId
      )
      
      Spacer(modifier = Modifier.height(8.dp))
      
      PlayerList(
        players = gameState.players,
        modifier = Modifier.weight(1f)
      )
    }
    
  }
}
