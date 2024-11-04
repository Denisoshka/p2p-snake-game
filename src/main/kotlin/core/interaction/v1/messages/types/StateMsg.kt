package d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.messages.v1.Direction
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.messages.v1.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.messages.v1.SnakeState

class StateMsg(
  var stateOrder: Int, // Порядковый номер состояния, уникален в пределах игры, монотонно возрастает
  var snakes: List<Snake>, // Список змей
  var foods: List<Coord>, // Список клеток с едой
  var players: List<d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer> // Актуальнейший список игроков
) : d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.Msg(MessageType.StateMsg) {
  class Coord(var x: Int, var y: Int)
  class Snake(
    var snakeState: SnakeState,
    var playerId: Int,
    var cords: List<Coord>,
    var direction: Direction
  )
}