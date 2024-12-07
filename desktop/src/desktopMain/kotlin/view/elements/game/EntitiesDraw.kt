package d.zhdanov.ccfit.nsu.view.elements.game

import androidx.annotation.IntRange
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Snake

fun getColorById(
  id: Int,
  @IntRange(from = 0, to = 0xFF) alpha: Int = 255
): Color {
  val r = (id * 37) % 256
  val g = (id * 53) % 256
  val b = (id * 97) % 256
  return Color(r, g, b)
}

fun DrawScope.drawSnakes(
  snakes: List<Snake>,
  currentPlayerId: Int,
  lineWidth: Float = 10f,
) {
  snakes.forEach { snake ->
    drawPath(
      path = Path().apply {
        snake.cords.forEachIndexed { index, position ->
          if(index == 0) moveTo(position.x.toFloat(), position.y.toFloat())
          else lineTo(position.x.toFloat(), position.y.toFloat())
        }
      },
      color = if(snake.playerId == currentPlayerId) {
        getColorById(snake.playerId)
      } else {
        getColorById(snake.playerId, 192)
      },
      style = Stroke(width = lineWidth, cap = StrokeCap.Round)
    )
  }
}

fun DrawScope.drawFood(
  food: List<Coord>,
  foodRadius: Float = 10f,
  foodColor: Color = Color.Green
) {
  food.forEach { foodItem ->
    drawCircle(
      color = foodColor,
      radius = foodRadius,
      center = Offset(foodItem.x.toFloat(), foodItem.y.toFloat())
    )
  }
}