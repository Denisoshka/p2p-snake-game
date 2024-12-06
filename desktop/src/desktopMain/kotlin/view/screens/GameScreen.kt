package d.zhdanov.ccfit.nsu.view.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GameConfig
import d.zhdanov.ccfit.nsu.view.elements.game.GameField
import d.zhdanov.ccfit.nsu.view.elements.game.GameHeader

@Composable
fun GameScreen(
  gameState: GameState,
  onExitGame: () -> Unit
) {
  Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    // Заголовок и кнопка выхода
    GameHeader(gameName = gameState.gameName, onExit = onExitGame)

    Spacer(modifier = Modifier.height(8.dp))

    // Игровое поле
    GameField(
      snakes = gameState.snakes,
      food = gameState.food,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Список игроков
    PlayerList(
      players = gameState.players,
      modifier = Modifier.weight(1f)
    )
  }
}
