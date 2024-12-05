package d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Coord
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.GamePlayer
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.MessageType
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.Snake

data class StateMsg(
  var stateOrder: Int, // Порядковый номер состояния, уникален в пределах игры, монотонно возрастает
  var snakes: MutableList<Snake>, // Список змей
  var foods: MutableList<Coord>, // Список клеток с едой
  var players: MutableList<GamePlayer> // Актуальнейший список игроков
) : Msg(MessageType.StateMsg)