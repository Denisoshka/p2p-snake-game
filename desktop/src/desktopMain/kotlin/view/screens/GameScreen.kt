package d.zhdanov.ccfit.nsu.view.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.view.elements.game.GameField
import d.zhdanov.ccfit.nsu.view.elements.game.GameHeader
import d.zhdanov.ccfit.nsu.view.elements.game.PlayerList

@Composable
fun GameScreen(
  gameState: StateMsg,
  gameName: String,
  currentPlayerId: Int,
  onExitGame: () -> Unit
) {
  Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    GameHeader(gameName = gameName, onExit = onExitGame)

    Spacer(modifier = Modifier.height(8.dp))

    GameField(
      snakes = gameState.snakes,
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
