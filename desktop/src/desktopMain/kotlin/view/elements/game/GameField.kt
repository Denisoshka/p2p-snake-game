package d.zhdanov.ccfit.nsu.view.elements.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Snake

@Composable
fun GameField(
  snakes: List<Snake>,
  food: List<Coord>,
  currentPlayerId: Int,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier.background(Color.LightGray)) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawSnakes(snakes, currentPlayerId)
      drawFood(food)
    }
  }
}
