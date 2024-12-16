package d.zhdanov.ccfit.nsu.core.game.engine.entity

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.game.engine.NetworkGameContext
import d.zhdanov.ccfit.nsu.core.game.engine.GameMap

interface Entity {
  val hitBox: List<GameMap.Cell>
  val head: GameMap.Cell
  val type: GameType
  val alive: Boolean
  val networkGameContext: NetworkGameContext
  fun update()
  fun hitBoxTravel(function: (x: Int, y: Int) -> Unit)
  fun checkCollisions(entity: Entity)
  fun atDead()
  fun shootState(state: SnakesProto.GameState.Builder)
}