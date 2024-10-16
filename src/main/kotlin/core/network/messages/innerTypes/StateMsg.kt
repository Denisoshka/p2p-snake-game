package d.zhdanov.ccfit.nsu.core.network.messages.innerTypes

import d.zhdanov.ccfit.nsu.core.network.messages.Direction
import d.zhdanov.ccfit.nsu.core.network.messages.SnakeState

data class GameState(
    val stateOrder: Int,              // Порядковый номер состояния
    val snakes: List<Snake>,          // Список змей
    val foods: List<Coord>,           // Список клеток с едой
    val players: GamePlayers          // Актуальный список игроков
) {
    // Координаты (либо смещение)
    data class Coord(
        val x: Int,  // По горизонтальной оси, положительное направление - вправо
        val y: Int   // По вертикальной оси, положительное направление - вниз
    )

    // Змея
    data class Snake(
        val playerId: Int,              // Идентификатор игрока-владельца змеи
        val points: List<Coord>,        // Список "ключевых" точек змеи (голова и смещения)
        val state: SnakeState,          // Статус змеи в игре (ALIVE или ZOMBIE)
        val headDirection: Direction    // Направление, в котором "повёрнута" голова змеи
    )

    // Игроки
    data class GamePlayers(
        val players: List<GamePlayer>  // Список игроков
    )

    // Игрок
    data class GamePlayer(
        val id: Int,           // Идентификатор игрока
        val name: String       // Имя игрока
    )
}
