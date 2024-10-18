package d.zhdanov.ccfit.nsu.core.interaction.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.messages.Direction
import d.zhdanov.ccfit.nsu.core.interaction.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.messages.SnakeState

class StateMsg(
  var stateOrder: Int, // Порядковый номер состояния, уникален в пределах игры, монотонно возрастает
  var snakes: List<Snake>, // Список змей
  var foods: List<Coord>, // Список клеток с едой
  var players: List<GamePlayer> // Актуальнейший список игроков
) {
  class Coord(var x: Int, var y: Int)
  class Snake(
    var snakeState: SnakeState,
    var playerId: Int,
    var cords: List<Coord>,
    var direction: Direction
  )
}